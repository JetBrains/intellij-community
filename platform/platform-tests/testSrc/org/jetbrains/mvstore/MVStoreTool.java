/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import io.netty.buffer.*;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.integratedBinaryPacking.LongBitPacker;
import org.jetbrains.mvstore.type.KeyableDataType;
import org.jetbrains.mvstore.type.StringDataType;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Utility methods used in combination with the MVStore.
 */
public final class MVStoreTool {

    /**
     * Runs this tool.
     * Options are case sensitive. Supported options are:
     * <table summary="command line options">
     * <tr><td>[-dump &lt;fileName&gt;]</td>
     * <td>Dump the contends of the file</td></tr>
     * <tr><td>[-info &lt;fileName&gt;]</td>
     * <td>Get summary information about a file</td></tr>
     * <tr><td>[-compact &lt;fileName&gt;]</td>
     * <td>Compact a store</td></tr>
     * <tr><td>[-compress &lt;fileName&gt;]</td>
     * <td>Compact a store with compression enabled</td></tr>
     * </table>
     *
     * @param args the command line arguments
     */
    public static void main(String... args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            if ("-dump".equals(args[i])) {
                String fileName = args[++i];
                dump(fileName, new PrintWriter(System.out), false);
            } else if ("-info".equals(args[i])) {
                String fileName = args[++i];
                info(fileName, new PrintWriter(System.out));
            } else if ("-compact".equals(args[i])) {
                String fileName = args[++i];
                compact(fileName, false);
            } else if ("-compress".equals(args[i])) {
                String fileName = args[++i];
                compact(fileName, true);
            } else if ("-rollback".equals(args[i])) {
                String fileName = args[++i];
                long targetVersion = Long.decode(args[++i]);
                rollback(fileName, targetVersion, new PrintWriter(System.out));
            } else if ("-repair".equals(args[i])) {
                String fileName = args[++i];
                repair(fileName);
            }
        }
    }

    /**
     * Read the contents of the file and write them to system out.
     *
     * @param fileName the name of the file
     * @param details whether to print details
     */
    public static void dump(String fileName, boolean details) throws IOException {
        dump(fileName, new OutputStreamWriter(System.out, StandardCharsets.UTF_8), details);
    }

    /**
     * Read the summary information of the file and write them to system out.
     *
     * @param fileName the name of the file
     */
    public static void info(String fileName) throws IOException {
        info(fileName, new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)));
    }

    /**
     * Read the contents of the file and display them in a human-readable
     * format.
     *
     * @param fileName the name of the file
     * @param writer the print writer
     * @param details print the page details
     */
    public static void dump(String fileName, Writer writer, boolean details) throws IOException {
        PrintWriter printer = new PrintWriter(writer, true);
        Path dbFile = Paths.get(fileName);
        if (!Files.exists(dbFile)) {
            printer.println("File not found: " + fileName);
            return;
        }

        long size = Files.size(dbFile);
        printer.printf("File %s, %d bytes, %s\n", fileName, size, StringUtilRt.formatFileSize(size));
        int blockSize = MVStore.BLOCK_SIZE;
        TreeMap<Integer, Long> mapSizesTotal = new TreeMap<>();
        long pageSizeTotal = 0;
        try (FileChannel file = FileChannel.open(dbFile, FileStore.R)) {
            long fileSize = file.size();
            int len = Long.toHexString(fileSize).length();
            ByteBuf block = UnpooledByteBufAllocator.DEFAULT.heapBuffer(4096);
            long pageCount = 0;
            for (long position = 0; position < fileSize; ) {
                block.clear();
                // Bugfix - An MVStoreException that wraps EOFException is
                // thrown when partial writes happens in the case of power off
                // or file system issues.
                // So we should skip the broken block at end of the DB file.
                try {
                    DataUtil.readFully(file, position, 4096, block);
                } catch (MVStoreException e) {
                    position += blockSize;
                    printer.printf("ERROR illegal position %d%n", position);
                    continue;
                }

                int headerType = block.readByte();
                if (headerType == 'm') {
                    if (block.readByte() != 'v') {
                        throw new MVStoreException(MVStoreException.ERROR_FILE_CORRUPT, "Corrupted file, expected first two bytes as 'mv'");
                    }
                    block.readerIndex(block.readerIndex() - 2);
                    MVStore.StoreHeader storeHeader = new MVStore.StoreHeader();
                    storeHeader.read(block);
                    printer.println(storeHeader.toString());
                    position += blockSize;
                    continue;
                }
                //if (headerType != 'c') {
                //    position += blockSize;
                //    continue;
                //}

                block.readerIndex(0);
                Chunk chunk;
                try {
                    chunk = Chunk.readChunkHeader(block, position);
                } catch (MVStoreException e) {
                    position += blockSize;
                    continue;
                }
                if (chunk.blockCount <= 0) {
                    // not a chunk
                    position += blockSize;
                    continue;
                }
                int length = chunk.blockCount * MVStore.BLOCK_SIZE;
                printer.printf("%n%0" + len + "x chunkHeader %s%n", position, chunk.toString());
                ByteBuf chunkBuf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(length);
                DataUtil.readFully(file, position, length, chunkBuf);
                int p = block.readerIndex();
                position += length;
                int remaining = chunk.pageCount;
                pageCount += chunk.pageCount;
                TreeMap<Integer, Integer> mapSizes = new TreeMap<>();

                int pageSizeSum = 0;
                while (remaining > 0) {
                    int start = p;
                    try {
                        chunkBuf.readerIndex(p);
                    } catch (IllegalArgumentException e) {
                        // too far
                        printer.printf("ERROR illegal position %d%n", p);
                        break;
                    }
                    int pageSize = chunkBuf.readInt();
                    // check value (ignored)
                    chunkBuf.readShort();
                    int mapId = IntBitPacker.readVar(chunkBuf);
                    /*int pageNo =*/
                    IntBitPacker.readVar(chunkBuf);
                    int keyCount = IntBitPacker.readVar(chunkBuf);
                    int type = chunkBuf.readByte();
                    boolean compressed = (type & Page.PAGE_COMPRESSED) != 0;
                    boolean highCompressed = (type & Page.PAGE_COMPRESSED_HIGH) != 0;
                    boolean node = (type & 1) != 0;
                    if (details) {
                        printer.printf(
                          "+%0" + len +
                                        "x %s, map %x, %d entries, %d bytes, maxLen %x%n",
                          p,
                          (node ? "node" : "leaf") + (compressed ? " compressed" + (highCompressed ? " (high)" : "") : ""),
                          mapId,
                          node ? keyCount + 1 : keyCount,
                          pageSize,
                          DataUtil.getPageMaxLength(DataUtil.getPageInfo(0, 0, pageSize, 0))
                        );
                    }
                    p += pageSize;
                    Integer mapSize = mapSizes.get(mapId);
                    if (mapSize == null) {
                        mapSize = 0;
                    }
                    mapSizes.put(mapId, mapSize + pageSize);
                    Long total = mapSizesTotal.get(mapId);
                    if (total == null) {
                        total = 0L;
                    }
                    mapSizesTotal.put(mapId, total + pageSize);
                    pageSizeSum += pageSize;
                    pageSizeTotal += pageSize;
                    remaining--;
                    long[] children = null;
                    long[] counts = null;
                    if (node) {
                        children = new long[keyCount + 1];
                        for (int i = 0; i <= keyCount; i++) {
                            children[i] = chunkBuf.readLong();
                        }
                        counts = new long[keyCount + 1];
                        for (int i = 0; i <= keyCount; i++) {
                          long s = LongBitPacker.readVar(chunkBuf);
                            counts[i] = s;
                        }
                    }
                    String[] keys = new String[keyCount];
                    if (mapId == 0 && details) {
                        ByteBuf data;
                        if (compressed) {
                            //boolean fast = (type & DataUtils.PAGE_COMPRESSED_HIGH) != DataUtils.PAGE_COMPRESSED_HIGH;
                            int lenAdd = IntBitPacker.readVar(chunkBuf);
                            int compLen = pageSize + start - chunkBuf.readerIndex();
                            byte[] comp = new byte[compLen];
                            chunkBuf.readBytes(comp);
                            int l = compLen + lenAdd;
                            data = UnpooledByteBufAllocator.DEFAULT.heapBuffer(l);
                            LZ4FastDecompressor decompressor = LZ4Factory.fastestJavaInstance().fastDecompressor();
                            decompressor.decompress(comp, 0, data.array(), 0, l);
                        } else {
                            data = chunkBuf;
                        }
                        for (int i = 0; i < keyCount; i++) {
                            String k = StringDataType.INSTANCE.read(data);
                            keys[i] = k;
                        }
                        if (node) {
                            // meta map node
                            for (int i = 0; i < keyCount; i++) {
                                long cp = children[i];
                                printer.printf("    %d children < %s @ " +
                                                "chunk %x +%0" +
                                                len + "x%n",
                                               counts[i],
                                               keys[i],
                                               DataUtil.getPageChunkId(cp),
                                               DataUtil.getPageOffset(cp));
                            }
                            long cp = children[keyCount];
                            printer.printf("    %d children >= %s @ chunk %x +%0" +
                                            len + "x%n",
                                           counts[keyCount],
                                    keys.length >= keyCount ? null : keys[keyCount],
                                           DataUtil.getPageChunkId(cp),
                                           DataUtil.getPageOffset(cp));
                        } else {
                            // meta map leaf
                            String[] values = new String[keyCount];
                            for (int i = 0; i < keyCount; i++) {
                                String v = StringDataType.INSTANCE.read(data);
                                values[i] = v;
                            }
                            for (int i = 0; i < keyCount; i++) {
                                printer.println("    " + keys[i] +
                                        " = " + values[i]);
                            }
                        }
                    } else {
                        if (node && details) {
                            for (int i = 0; i <= keyCount; i++) {
                                long cp = children[i];
                                printer.printf("    %d children @ chunk %x +%0" +
                                                len + "x%n",
                                               counts[i],
                                               DataUtil.getPageChunkId(cp),
                                               DataUtil.getPageOffset(cp));
                            }
                        }
                    }
                }
                pageSizeSum = Math.max(1, pageSizeSum);
                for (Integer mapId : mapSizes.keySet()) {
                    int percent = 100 * mapSizes.get(mapId) / pageSizeSum;
                    printer.printf("map %x: %d bytes, %d%%%n", mapId, mapSizes.get(mapId), percent);
                }
                int footerPos = chunkBuf.capacity() - Chunk.FOOTER_LENGTH;
                try {
                    chunkBuf.readerIndex(footerPos);
                    byte[] footerBytes = ByteBufUtil.getBytes(chunkBuf, chunkBuf.readerIndex(), Chunk.FOOTER_LENGTH);
                    printer.printf("+%0" + len + "x chunkFooter %s%n", footerPos, StringUtil.toHexString(footerBytes));
                } catch (IllegalArgumentException e) {
                    // too far
                    printer.printf("ERROR illegal footer position %d%n", footerPos);
                }
            }
            printer.printf("%n%0" + len + "x eof%n", fileSize);
            printer.printf("\n");
            pageCount = Math.max(1, pageCount);
            printer.printf("page size total: %d bytes, page count: %d, average page size: %d bytes\n",
                    pageSizeTotal, pageCount, pageSizeTotal / pageCount);
            pageSizeTotal = Math.max(1, pageSizeTotal);
            for (Integer mapId : mapSizesTotal.keySet()) {
                int percent = (int) (100 * mapSizesTotal.get(mapId) / pageSizeTotal);
                printer.printf("map %x: %d bytes, %d%%%n", mapId, mapSizesTotal.get(mapId), percent);
            }
        } catch (IOException e) {
            printer.println("ERROR: " + e);
            e.printStackTrace(printer);
        }
        // ignore
        printer.flush();
    }

    /**
     * Read the summary information of the file and write them to system out.
     *
     * @param fileName the name of the file
     * @param writer the print writer
     * @return null if successful (if there was no error), otherwise the error
     *         message
     */
    public static String info(String fileName, Writer writer) throws IOException {
        PrintWriter pw = new PrintWriter(writer, true);
        Path dbFile = Paths.get(fileName);
        if (!Files.exists(dbFile)) {
            pw.println("File not found: " + fileName);
            return "File not found: " + fileName;
        }
        long fileLength = Files.size(dbFile);
        try (MVStore store = new MVStore.Builder().recoveryMode().readOnly().open(dbFile)) {
            MVMap<Integer, byte[]> chunkMap = store.getChunkMap();
            MVStore.StoreHeader header = store.getStoreHeader();
            long fileCreated = header.creationTime;
            TreeMap<Integer, Chunk> chunks = new TreeMap<>();
            long chunkLength = 0;
            long maxLength = 0;
            long maxLengthLive = 0;
            long maxLengthNotEmpty = 0;
            for (Entry<Integer, byte[]> entry : chunkMap.entrySet()) {
                Chunk c = Chunk.readMetadata(entry.getKey(), Unpooled.wrappedBuffer(entry.getValue()));
                chunks.put(c.id, c);
                chunkLength += (long)c.blockCount * MVStore.BLOCK_SIZE;
                maxLength += c.maxLen;
                maxLengthLive += c.maxLenLive;
                if (c.maxLenLive > 0) {
                    maxLengthNotEmpty += c.maxLen;
                }
            }
            pw.printf("Created: %s\n", formatTimestamp(fileCreated, fileCreated));
            pw.printf("Last modified: %s\n",
                    formatTimestamp(Files.getLastModifiedTime(dbFile).toMillis(), fileCreated));
            pw.printf("File length: %d\n", fileLength);
            pw.printf("The last chunk is not listed\n");
            pw.printf("Chunk length: %d\n", chunkLength);
            pw.printf("Chunk count: %d\n", chunks.size());
            pw.printf("Used space: %d%%\n", getPercent(chunkLength, fileLength));
            pw.printf("Chunk fill rate: %d%%\n", maxLength == 0 ? 100 :
                getPercent(maxLengthLive, maxLength));
            pw.printf("Chunk fill rate excluding empty chunks: %d%%\n",
                maxLengthNotEmpty == 0 ? 100 :
                getPercent(maxLengthLive, maxLengthNotEmpty));
            for (Entry<Integer, Chunk> e : chunks.entrySet()) {
                Chunk c = e.getValue();
                long created = fileCreated + c.time;
                pw.printf("  Chunk %d: %s, %d%% used, %d blocks",
                        c.id, formatTimestamp(created, fileCreated),
                        getPercent(c.maxLenLive, c.maxLen),
                        c.blockCount
                        );
                if (c.maxLenLive == 0) {
                    pw.printf(", unused: %s",
                            formatTimestamp(fileCreated + c.unused, fileCreated));
                }
                pw.printf("\n");
            }
            pw.printf("\n");
        } catch (Exception e) {
            pw.println("ERROR: " + e);
            e.printStackTrace(pw);
            return e.getMessage();
        }
        pw.flush();
        return null;
    }

    private static String formatTimestamp(long t, long start) {
        String x = new Timestamp(t).toString();
        String s = x.substring(0, 19);
        s += " (+" + ((t - start) / 1000) + " s)";
        return s;
    }

    private static int getPercent(long value, long max) {
        if (value == 0) {
            return 0;
        } else if (value == max) {
            return 100;
        }
        return (int) (1 + 98 * value / Math.max(1, max));
    }

    /**
     * Compress the store by creating a new file and copying the live pages
     * there. Temporarily, a file with the suffix ".tempFile" is created. This
     * file is then renamed, replacing the original file, if possible. If not,
     * the new file is renamed to ".newFile", then the old file is removed, and
     * the new file is renamed. This might be interrupted, so it's better to
     * compactCleanUp before opening a store, in case this method was used.
     *
     * @param fileName the file name
     * @param compress whether to compress the data
     */
    public static void compact(String fileName, boolean compress) throws IOException {
        String tempName = fileName + ".tempFile";
        Files.delete(Paths.get(tempName));
        compact(fileName, tempName, compress);
        //try {
        //    // todo
        //    //Files.moveAtomicReplace(tempName, fileName);
        //    //Files.move(Paths.get(tempName), Paths.get(newName), StandardCopyOption.REPLACE_EXISTING);
        //}
        //catch (UncheckedIOException e) {
        //    String newName = fileName + ".newFile";
        //    Files.move(Paths.get(tempName), Paths.get(newName), StandardCopyOption.REPLACE_EXISTING);
        //    Files.move(Paths.get(newName), Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
        //}
    }

    /**
     * Copy all live pages from the source store to the target store.
     *
     * @param sourceFileName the name of the source store
     * @param targetFileName the name of the target store
     * @param compress whether to compress the data
     */
    public static void compact(String sourceFileName, String targetFileName, boolean compress) {
        //try (MVStore source = new MVStore.Builder().readOnly().open(new FileStore(sourceFileName, true))) {
        //    // Bugfix - Add double "try-finally" statements to close source and target stores for
        //    //releasing lock and file resources in these stores even if OOM occurs.
        //    // Fix issues such as "Cannot delete file "/h2/data/test.mv.db.tempFile" [90025-197]"
        //    //when client connects to this server and reopens this store database in this process.
        //    // @since 2018-09-13 little-pan
        //    FileUtils.delete(targetFileName);
        //    MVStore.Builder b = new MVStore.Builder();
        //    if (compress) {
        //        b.compress();
        //    }
        //    try (MVStore target = b.open(new FileStore(targetFileName, false))) {
        //        compact(source, target);
        //    }
        //}
    }

    ///**
    // * Copy all live pages from the source store to the target store.
    // *
    // * @param source the source store
    // * @param target the target store
    // */
    //public static void compact(MVStore source, MVStore target) {
    //    int autoCommitDelay = target.getAutoCommitDelay();
    //    boolean reuseSpace = target.getReuseSpace();
    //    try {
    //        target.setReuseSpace(false);  // disable unused chunks collection
    //        //target.setAutoCommitDelay(0); // disable autocommit
    //        MVMap<String, String> sourceMeta = source.getMetaMap();
    //        MVMap<String, String> targetMeta = target.getMetaMap();
    //        for (Entry<String, String> m : sourceMeta.entrySet()) {
    //            String key = m.getKey();
    //            if (key.startsWith(DataUtils.META_MAP)) {
    //                // ignore
    //            } else if (key.startsWith(MVStore.META_NAME)) {
    //                // ignore
    //            } else {
    //                targetMeta.put(key, m.getValue());
    //            }
    //        }
    //        // We are going to cheat a little bit in the copyFrom() by employing "incomplete" pages,
    //        // which would be spared of saving, but save completed pages underneath,
    //        // and those may appear as dead (non-reachable).
    //        // That's why it is important to preserve all chunks
    //        // created in the process, especially if retention time
    //        // is set to a lower value, or even 0.
    //        for (String mapName : source.getMapNames()) {
    //            MVMap.Builder<Object, Object> mp = getGenericMapBuilder();
    //            // This is a hack to preserve chunks occupancy rate accounting.
    //            // It exposes design deficiency flaw in MVStore related to lack of
    //            // map's type metadata.
    //            // TODO: Introduce type metadata which will allow to open any store
    //            // TODO: without prior knowledge of keys / values types and map implementation
    //            // TODO: (MVMap vs MVRTreeMap, regular vs. singleWriter etc.)
    //            //if (mapName.startsWith(TransactionStore.UNDO_LOG_NAME_PREFIX)) {
    //            //    mp.singleWriter();
    //            //}
    //            MVMap<Object, Object> sourceMap = source.openMap(mapName, mp);
    //            MVMap<Object, Object> targetMap = target.openMap(mapName, mp);
    //            targetMap.copyFrom(sourceMap);
    //        }
    //        // this will end hacky mode of operation with incomplete pages
    //        // end ensure that all pages are saved
    //        target.commit();
    //    } finally {
    //        //target.setAutoCommitDelay(autoCommitDelay);
    //        target.setReuseSpace(reuseSpace);
    //    }
    //}

    /**
     * Repair a store by rolling back to the newest good version.
     *
     * @param fileName the file name
     */
    public static void repair(String fileName) {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        long version = Long.MAX_VALUE;
        OutputStream ignore = new OutputStream() {
            @Override
            public void write(int b) {
                // ignore
            }
        };
        while (version >= 0) {
            pw.println(version == Long.MAX_VALUE ? "Trying latest version"
                    : ("Trying version " + version));
            pw.flush();
            version = rollback(fileName, version, new PrintWriter(ignore));
            try {
                String error = info(fileName + ".temp", new PrintWriter(ignore));
                if (error == null) {
                    Files.move(Paths.get(fileName), Paths.get(fileName + ".back"));
                    Files.move(Paths.get(fileName + ".temp"), Paths.get(fileName + ".back"));
                    pw.println("Success");
                    break;
                }
                pw.println("    ... failed: " + error);
            } catch (Exception e) {
                pw.println("Fail: " + e.getMessage());
                pw.flush();
            }
            version--;
        }
        pw.flush();
    }

    /**
     * Roll back to a given revision into a file called *.temp.
     *
     * @param fileName the file name
     * @param targetVersion the version to roll back to (Long.MAX_VALUE for the
     *            latest version)
     * @param writer the log writer
     * @return the version rolled back to (-1 if no version)
     */
    public static long rollback(String fileName, long targetVersion, Writer writer) {
        long newestVersion = -1;
        PrintWriter pw = new PrintWriter(writer, true);
        Path dbFile = Paths.get(fileName);
        if (!Files.exists(dbFile)) {
            pw.println("File not found: " + fileName);
            return newestVersion;
        }
        FileChannel file = null;
        FileChannel target = null;
        int blockSize = MVStore.BLOCK_SIZE;
        try {
            file = FileChannel.open(dbFile, FileStore.R);
            Files.delete(Paths.get(fileName + ".temp"));
            target = FileChannel.open(Paths.get(fileName + ".temp"), FileStore.RW);
            long fileSize = file.size();
            ByteBuf block = UnpooledByteBufAllocator.DEFAULT.heapBuffer(4096);
            Chunk newestChunk = null;
            for (long pos = 0; pos < fileSize;) {
                //block.rewind();
                DataUtil.readFully(file, pos, 4096, block);
                //block.rewind();
                int headerType = block.readByte();
                if (headerType == 'H') {
                    //block.rewind();
                    block.readBytes(target, pos, block.readableBytes());
                    pos += blockSize;
                    continue;
                }
                if (headerType != 'c') {
                    pos += blockSize;
                    continue;
                }
                Chunk c;
                try {
                    c = Chunk.readChunkHeader(block, pos);
                } catch (MVStoreException e) {
                    pos += blockSize;
                    continue;
                }
                if (c.blockCount <= 0) {
                    // not a chunk
                    pos += blockSize;
                    continue;
                }
                int length = c.blockCount * MVStore.BLOCK_SIZE;
                ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(length);
                try {
                    DataUtil.readFully(file, pos, length, buf);
                    if (c.version > targetVersion) {
                        // newer than the requested version
                        pos += length;
                        continue;
                    }
                    buf.readBytes(target, pos, buf.readableBytes());
                }
                finally {
                    buf.release();
                }
                if (newestChunk == null || c.version > newestChunk.version) {
                    newestChunk = c;
                    newestVersion = c.version;
                }
                pos += length;
            }
            int length = newestChunk.blockCount * MVStore.BLOCK_SIZE;
            ByteBuf chunk = PooledByteBufAllocator.DEFAULT.ioBuffer(length);
            try {
                DataUtil.readFully(file, newestChunk.block * MVStore.BLOCK_SIZE, length, chunk);
                chunk.readBytes(target, fileSize, length);
            }
            finally {
                chunk.release();
            }
        } catch (IOException e) {
            pw.println("ERROR: " + e);
            e.printStackTrace(pw);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (target != null) {
                try {
                    target.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        pw.flush();
        return newestVersion;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static MVMap.Builder<Object,Object> getGenericMapBuilder() {
        return (MVMap.Builder)new MVMap.Builder<byte[],byte[]>().
                keyType(GenericDataType.INSTANCE).
                valueType(GenericDataType.INSTANCE);
    }

    /**
     * A data type that can read any data that is persisted, and converts it to
     * a byte array.
     */
    private static final class GenericDataType implements KeyableDataType<byte[]> {
        static GenericDataType INSTANCE = new GenericDataType();

        @Override
        public int compare(byte[] data1, byte[] data2) {
            if (data1 == data2) {
                return 0;
            }
            int len = Math.min(data1.length, data2.length);
            for (int i = 0; i < len; i++) {
                int b = data1[i] & 255;
                int b2 = data2[i] & 255;
                if (b != b2) {
                    return b > b2 ? 1 : -1;
                }
            }
            return Integer.signum(data1.length - data2.length);
        }

        @Override
        public int getMemory(byte[] obj) {
            return obj == null ? 0 : obj.length * 8;
        }

        @Override
        public int getFixedMemory() {
            return -1;
        }

        @Override
        public byte[][] createStorage(int size) {
            return new byte[size][];
        }

        @Override
        public void write(ByteBuf buf, byte[] obj) {
            if (obj != null) {
                buf.writeBytes(obj);
            }
        }

        @Override
        public byte[] read(ByteBuf buff) {
            int len = buff.readableBytes();
            if (len == 0) {
                return null;
            }
            byte[] data = new byte[len];
            buff.readBytes(data);
            return data;
        }
    }
}
