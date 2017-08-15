/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.CompressionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.storage.*;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;

/**
 * @author max
 */
@SuppressWarnings({"PointlessArithmeticExpression", "HardCodedStringLiteral"})
public class FSRecords implements IFSRecords {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  private static final boolean lazyVfsDataCleaning = SystemProperties.getBooleanProperty("idea.lazy.vfs.data.cleaning", true);
  static final boolean backgroundVfsFlush = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);
  private static final boolean inlineAttributes = SystemProperties.getBooleanProperty("idea.inline.vfs.attributes", true);
  static final boolean bulkAttrReadSupport = SystemProperties.getBooleanProperty("idea.bulk.attr.read", false);
  static final boolean useSnappyForCompression = SystemProperties.getBooleanProperty("idea.use.snappy.for.vfs", false);
  private static final boolean useSmallAttrTable = SystemProperties.getBooleanProperty("idea.use.small.attr.table.for.vfs", true);
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");
  private static final boolean ourStoreRootsSeparately = SystemProperties.getBooleanProperty("idea.store.roots.separately", false);
  public static boolean weHaveContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);

  private static final int VERSION = 21 + (weHaveContentHashes ? 0x10:0) + (IOUtil.ourByteBuffersUseNativeByteOrder ? 0x37:0) +
                                     31 + (bulkAttrReadSupport ? 0x27:0) + (inlineAttributes ? 0x31 : 0) +
                                     (ourStoreRootsSeparately ? 0x63 : 0) +
                                     (useSnappyForCompression ? 0x7f : 0) + (useSmallAttrTable ? 0x31 : 0) +
                                     (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 21:0);

  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTR_REF_SIZE = 4;
  private static final int CONTENT_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
  private static final int CONTENT_SIZE = 4;
  private static final int TIMESTAMP_OFFSET = CONTENT_OFFSET + CONTENT_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MOD_COUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MOD_COUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
  private static final int LENGTH_SIZE = 8;

  private static final int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;

  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private static final int HEADER_VERSION_OFFSET = 0;
  //private static final int HEADER_RESERVED_4BYTES_OFFSET = 4; // reserved
  private static final int HEADER_GLOBAL_MOD_COUNT_OFFSET = 8;
  private static final int HEADER_CONNECTION_STATUS_OFFSET = 12;
  private static final int HEADER_TIMESTAMP_OFFSET = 16;
  private static final int HEADER_SIZE = HEADER_TIMESTAMP_OFFSET + 8;

  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;
  private static final int CORRUPTED_MAGIC = 0xabcf7f7f;

  private static final FileAttribute ourChildrenAttr = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");

  private final ReentrantReadWriteLock.ReadLock r;
  private final ReentrantReadWriteLock.WriteLock w;

  private volatile int myLocalModificationCount;
  private volatile boolean myIsDisposed;

  private static final int FREE_RECORD_FLAG = 0x100;
  private static final int ALL_VALID_FLAGS = PersistentFS.ALL_VALID_FLAGS | FREE_RECORD_FLAG;

  static File defaultBasePath() {
    return new File(getCachesDir());
  }

  static String getCachesDir() {
    String dir = System.getProperty("caches_dir"); //TODO: return here
    return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
  }

  static {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= RECORD_SIZE;
  }

  private File myBaseFile;

  public FSRecords(File baseFile) {
    myBaseFile = baseFile;
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    r = lock.readLock();
    w = lock.writeLock();
  }

  @Override
  public void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    w.lock();
    try {
      setName(id, name);

      setTimestamp(id, attributes.lastModified);
      setLength(id, attributes.isDirectory() ? -1L : attributes.length);

      setFlags(id, (attributes.isDirectory() ? PersistentFS.IS_DIRECTORY_FLAG : 0) |
                   (attributes.isWritable() ? 0 : PersistentFS.IS_READ_ONLY) |
                   (attributes.isSymLink() ? PersistentFS.IS_SYMLINK : 0) |
                   (attributes.isSpecial() ? PersistentFS.IS_SPECIAL : 0) |
                   (attributes.isHidden() ? PersistentFS.IS_HIDDEN : 0), true);
      setParent(id, parentId);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  void requestVfsRebuild(Throwable e) {
    //noinspection ThrowableResultOfMethodCallIgnored
    requestRebuild(e);
  }

  File basePath() {
    return myBaseFile;
  }

  private boolean myInitialized;

  private PersistentStringEnumerator myNames;
  private FileNameCache myNameCache;
  private Storage myAttributes;
  private RefCountingStorage myContents;
  private ResizeableMappedFile myRecords;
  private PersistentBTreeEnumerator<byte[]> myContentHashesEnumerator;
  private File myRootsFile;
  private VfsDependentEnum<String> myAttributesList;
  private final TIntArrayList myFreeRecords = new TIntArrayList();

  private boolean myDirty;
  private ScheduledFuture<?> myFlushingFuture;
  private boolean myCorrupted;

  private final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();


  @Override
  public void connect(PagedFileStorage.StorageLockContext lockContext, PersistentStringEnumerator names, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList) {
    w.lock();
    try {
      if (!myInitialized) {
        init(names, lockContext, fileNameCache, attrsList);
        setupFlushing();
        myInitialized = true;
      }
    }
    finally {
      w.unlock();
    }
  }

  private void scanFreeRecords() {
    final int filelength = (int)myRecords.length();
    LOG.assertTrue(filelength % RECORD_SIZE == 0, "invalid file size: " + filelength);

    int count = filelength / RECORD_SIZE;
    for (int n = 2; n < count; n++) {
      if (BitUtil.isSet(getFlags(n), FREE_RECORD_FLAG)) {
        myFreeRecords.add(n);
      }
    }
  }

  int getFreeRecord() {
    if (myFreeRecords.isEmpty()) return 0;
    return myFreeRecords.remove(myFreeRecords.size() - 1);
  }

  private void createBrokenMarkerFile(@Nullable Throwable reason) {
    final File brokenMarker = getCorruptionMarkerFile();

    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (PrintStream stream = new PrintStream(out)) {
        new Exception().printStackTrace(stream);
        if (reason != null) {
          stream.print("\nReason:\n");
          reason.printStackTrace(stream);
        }
      }
      LOG.info("Creating VFS corruption marker; Trace=\n" + out);

      try (FileWriter writer = new FileWriter(brokenMarker)) {
        writer.write("These files are corrupted and must be rebuilt from the scratch on next startup");
      }
    }
    catch (IOException e) {
      // No luck.
    }
  }

  private File getCorruptionMarkerFile() {
    return new File(basePath(), "corruption.marker");
  }

  private void init(PersistentStringEnumerator names, PagedFileStorage.StorageLockContext lockContext, FileNameCache fileNameCache, VfsDependentEnum<String> attrsList) {
    final File basePath = basePath().getAbsoluteFile();
    basePath.mkdirs();

    final File attributesFile = new File(basePath, "attrib" + VFS_FILES_EXTENSION);
    final File contentsFile = new File(basePath, "content" + VFS_FILES_EXTENSION);
    final File contentsHashesFile = new File(basePath, "contentHashes" + VFS_FILES_EXTENSION);
    final File recordsFile = new File(basePath, "records" + VFS_FILES_EXTENSION);
    myRootsFile = ourStoreRootsSeparately ? new File(basePath, "roots" + VFS_FILES_EXTENSION) : null;

    final File vfsDependentEnumBaseFile = VfsDependentEnum.getBaseFile();

    try {
      if (getCorruptionMarkerFile().exists()) {
        invalidateIndex("corruption marker found");
        throw new IOException("Corruption marker file found");
      }

      myNames = names;
      myNameCache = fileNameCache;
      myAttributesList = attrsList;

      myAttributes = new Storage(attributesFile.getPath(), REASONABLY_SMALL) {
        @Override
        protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
          return inlineAttributes && useSmallAttrTable
                 ? new CompactRecordsTable(recordsFile, pool, false)
                 : super.createRecordsTable(pool, recordsFile);
        }
      };
      myContents =
        new RefCountingStorage(contentsFile.getPath(), CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH, useSnappyForCompression) {
          @NotNull
          @Override
          protected ExecutorService createExecutor() {
            return AppExecutorUtil.createBoundedApplicationPoolExecutor("FSRecords pool", 1);
          }
        }; // sources usually zipped with 4x ratio
      myContentHashesEnumerator = weHaveContentHashes ? new ContentHashesUtil.HashEnumerator(contentsHashesFile, lockContext) : null;
      boolean aligned = PagedFileStorage.BUFFER_SIZE % RECORD_SIZE == 0;
      assert aligned; // for performance
      myRecords = new ResizeableMappedFile(recordsFile, 20 * 1024, lockContext,
                                           PagedFileStorage.BUFFER_SIZE, aligned, IOUtil.ourByteBuffersUseNativeByteOrder);

      if (myRecords.length() == 0) {
        cleanRecord(0); // Clean header
        cleanRecord(1); // Create root record
        setCurrentVersion();
      }

      if (getVersion() != VERSION) {
        throw new IOException("FS repository version mismatch");
      }

      if (myRecords.getInt(HEADER_CONNECTION_STATUS_OFFSET) != SAFELY_CLOSED_MAGIC) {
        throw new IOException("FS repository wasn't safely shut down");
      }
      markDirty();
      scanFreeRecords();
    }
    catch (Exception e) { // IOException, IllegalArgumentException
      LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
      try {
        closeFiles();

        boolean deleted = FileUtil.delete(getCorruptionMarkerFile());
        //deleted &= IOUtil.deleteAllFilesStartingWith(namesFile); // TODO!
        deleted &= AbstractStorage.deleteFiles(attributesFile.getPath());
        deleted &= AbstractStorage.deleteFiles(contentsFile.getPath());
        deleted &= IOUtil.deleteAllFilesStartingWith(contentsHashesFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(recordsFile);
        deleted &= IOUtil.deleteAllFilesStartingWith(vfsDependentEnumBaseFile);
        deleted &= myRootsFile == null || IOUtil.deleteAllFilesStartingWith(myRootsFile);

        if (!deleted) {
          throw new IOException("Cannot delete filesystem storage files");
        }
      }
      catch (final IOException e1) {
        final Runnable warnAndShutdown = () -> {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            //noinspection CallToPrintStackTrace
            e1.printStackTrace();
          }
          else {
            final String message = "Files in " + basePath.getPath() + " are locked.\n" +
                                   ApplicationNamesInfo.getInstance().getProductName() + " will not be able to start up.";
            if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
              JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, "Fatal Error", JOptionPane.ERROR_MESSAGE);
            }
            else {
              //noinspection UseOfSystemOutOrSystemErr
              System.err.println(message);
            }
          }
          Runtime.getRuntime().halt(1);
        };

        if (EventQueue.isDispatchThread()) {
          warnAndShutdown.run();
        }
        else {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(warnAndShutdown);
        }

        throw new RuntimeException("Can't rebuild filesystem storage ", e1);
      }

      init(names, lockContext, fileNameCache, attrsList);
    }
  }

  private void invalidateIndex(String reason) {
    LOG.info("Marking VFS as corrupted: " + reason);
    final File indexRoot = PathManager.getIndexRoot();
    if (indexRoot.exists()) {
      final String[] children = indexRoot.list();
      if (children != null && children.length > 0) {
        // create index corruption marker only if index directory exists and is non-empty
        // It is incorrect to consider non-existing indices "corrupted"
        FileUtil.createIfDoesntExist(new File(PathManager.getIndexRoot(), "corruption.marker"));
      }
    }
  }

  private void markDirty() {
    if (!myDirty) {
      myDirty = true;
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);
    }
  }

  private void setupFlushing() {
    if (!backgroundVfsFlush) {
      return;
    }

    myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
      private int lastModCount;

      @Override
      public void run() {
        if (lastModCount == myLocalModificationCount) {
          flush();
        }
        lastModCount = myLocalModificationCount;
      }
    });
  }

  @Override
  public void force() {
    w.lock();
    try {
      doForce();
    }
    finally {
      w.unlock();
    }
  }

  private void doForce() {
    if (myNames != null) {
      myNames.force();
      myAttributes.force();
      myContents.force();
      if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
      markClean();
      myRecords.force();
    }
  }

  private void flush() {
    if (!isDirty() || HeavyProcessLatch.INSTANCE.isRunning()) return;

    r.lock();
    try {
      if (myFlushingFuture == null) {
        return; // avoid NPE when close has already taken place
      }
      doForce();
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public boolean isDirty() {
    return myDirty || myNames.isDirty() || myAttributes.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
           myContentHashesEnumerator != null && myContentHashesEnumerator.isDirty();
  }


  private int getVersion() {
    final int recordsVersion = myRecords.getInt(HEADER_VERSION_OFFSET);
    if (myAttributes.getVersion() != recordsVersion || myContents.getVersion() != recordsVersion) return -1;

    return recordsVersion;
  }

  @Override
  public long getTimestamp() {
    return myRecords.getLong(HEADER_TIMESTAMP_OFFSET);
  }

  private void setCurrentVersion() {
    myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
    myRecords.putLong(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
    myAttributes.setVersion(VERSION);
    myContents.setVersion(VERSION);
    myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
  }

  void cleanRecord(int id) {
    myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
  }

  private void closeFiles() throws IOException {
    if (myFlushingFuture != null) {
      myFlushingFuture.cancel(false);
      myFlushingFuture = null;
    }

    if (myNames != null) {
      myNames.close();
      myNames = null;
    }

    if (myAttributes != null) {
      Disposer.dispose(myAttributes);
      myAttributes = null;
    }

    if (myContents != null) {
      Disposer.dispose(myContents);
      myContents = null;
    }

    if (myContentHashesEnumerator != null) {
      myContentHashesEnumerator.close();
      myContentHashesEnumerator = null;
    }

    if (myRecords != null) {
      markClean();
      myRecords.close();
      myRecords = null;
    }
    myInitialized = false;
  }

  private void markClean() {
    if (myDirty) {
      myDirty = false;
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, myCorrupted ? CORRUPTED_MAGIC : SAFELY_CLOSED_MAGIC);
    }
  }

  private static final int RESERVED_ATTR_ID = bulkAttrReadSupport ? 1 : 0;
  private static final int FIRST_ATTR_ID_OFFSET = bulkAttrReadSupport ? RESERVED_ATTR_ID : 0;

  private int getAttributeId(@NotNull String attId) throws IOException {
    return myAttributesList.getId(attId) + FIRST_ATTR_ID_OFFSET;
  }

  @Override
  public void requestRebuild(@NotNull Throwable e) throws RuntimeException, Error {
    if (!myIsDisposed) {
      // No need to forcibly mark VFS corrupted if it is already shut down
      if (!myCorrupted && w.tryLock()) { // avoid deadlock if r lock is occupied by current thread
        w.unlock();
        createBrokenMarkerFile(e);
        myCorrupted = true;
        force();
      }
    }

    if (e instanceof Error) throw (Error)e;
    if (e instanceof RuntimeException) throw (RuntimeException)e;
    throw new RuntimeException(e);
  }

  @Override
  public void requestRebuild(int fileId, @NotNull Throwable e) throws RuntimeException, Error {
    requestRebuild(e);
  }

  private static class AttrPageAwareCapacityAllocationPolicy extends CapacityAllocationPolicy {
    boolean myAttrPageRequested;

    @Override
    public int calculateCapacity(int requiredLength) {   // 20% for growth
      return Math.max(myAttrPageRequested ? 8 : 32, Math.min((int)(requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
    }
  }

  @Override
  public long getCreationTimestamp() {
    r.lock();
    try {
      return getTimestamp();
    }
    finally {
      r.unlock();
    }
  }

  private ResizeableMappedFile getRecords() {
    return myRecords;
  }

  private PersistentBTreeEnumerator<byte[]> getContentHashesEnumerator() {
    return myContentHashesEnumerator;
  }

  private RefCountingStorage getContentStorage() {
    return myContents;
  }

  private Storage getAttributesStorage() {
    return myAttributes;
  }

  @Override
  public int createChildRecord(int parentId) {
    return createRecord();
  }

  // todo: Address  / capacity store in records table, size store with payload
  public int createRecord() {
    w.lock();
    try {
      markDirty();

      final int free = getFreeRecord();
      if (free == 0) {
        final int fileLength = length();
        LOG.assertTrue(fileLength % RECORD_SIZE == 0);
        int newRecord = fileLength / RECORD_SIZE;
        cleanRecord(newRecord);
        assert fileLength + RECORD_SIZE == length();
        return newRecord;
      }
      else {
        if (lazyVfsDataCleaning) deleteContentAndAttributes(free);
        cleanRecord(free);
        return free;
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
    return -1;
  }

  private int length() {
    return (int)getRecords().length();
  }

  public int getMaxId() {
    r.lock();
    try {
      return length()/RECORD_SIZE;
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public void deleteRecordRecursively(int id) {
    w.lock();
    try {
      incModCount(id);
      if (lazyVfsDataCleaning) {
        markAsDeletedRecursively(id);
      } else {
        doDeleteRecursively(id);
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  private void markAsDeletedRecursively(final int id) {
    for (int subrecord : list(id)) {
      markAsDeletedRecursively(subrecord);
    }

    markAsDeleted(id);
  }

  private void markAsDeleted(final int id) {
    w.lock();
    try {
      markDirty();
      addToFreeRecordsList(id);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  private void doDeleteRecursively(final int id) {
    for (int subrecord : list(id)) {
      doDeleteRecursively(subrecord);
    }

    deleteRecord(id);
  }

  private void deleteRecord(final int id) {
    w.lock();
    try {
      markDirty();
      deleteContentAndAttributes(id);

      cleanRecord(id);
      addToFreeRecordsList(id);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  private void deleteContentAndAttributes(int id) throws IOException {
    int content_page = getContentRecordId(id);
    if (content_page != 0) {
      if (weHaveContentHashes) {
        getContentStorage().releaseRecord(content_page, false);
      } else {
        getContentStorage().releaseRecord(content_page);
      }
    }

    int att_page = getAttributeRecordId(id);
    if (att_page != 0) {
      final DataInputStream attStream = getAttributesStorage().readStream(att_page);
      if (bulkAttrReadSupport) skipRecordHeader(attStream, RESERVED_ATTR_ID, id);

      while (attStream.available() > 0) {
        DataInputOutputUtil.readINT(attStream);// Attribute ID;
        int attAddressOrSize = DataInputOutputUtil.readINT(attStream);

        if (inlineAttributes) {
          if(attAddressOrSize < MAX_SMALL_ATTR_SIZE) {
            attStream.skipBytes(attAddressOrSize);
            continue;
          }
          attAddressOrSize -= MAX_SMALL_ATTR_SIZE;
        }
        getAttributesStorage().deleteRecord(attAddressOrSize);
      }
      attStream.close();
      getAttributesStorage().deleteRecord(att_page);
    }
  }

  private void addToFreeRecordsList(int id) {
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    setFlags(id, FREE_RECORD_FLAG, false);
  }

  private static final int ROOT_RECORD_ID = 1;

  @Override
  @NotNull
  public RootRecord[] listRoots() {
    try {
      r.lock();
      try {
        if (ourStoreRootsSeparately) {
          ArrayList<RootRecord> result = new ArrayList<>();

          try {
            try (LineNumberReader stream = new LineNumberReader(new BufferedReader(new InputStreamReader(new FileInputStream(myRootsFile))))) {
              String str;
              while((str = stream.readLine()) != null) {
                int index = str.indexOf(' ');
                int id = Integer.parseInt(str.substring(0, index));
                String url = str.substring(index).trim();
                result.add(new RootRecord(id, url));
              }
            }
          } catch (FileNotFoundException ignored) {}

          return result.toArray(new RootRecord[0]);
        }

        final DataInputStream input = readAttributeNoLock(ROOT_RECORD_ID, ourChildrenAttr);
        if (input == null) return new RootRecord[0];

        try {
          final int count = DataInputOutputUtil.readINT(input);
          RootRecord[] result = new RootRecord[count];
          int prevId = 0;
          int prevNameId = 0;
          for (int i = 0; i < count; i++) {
            int nameId = DataInputOutputUtil.readINT(input) + prevNameId;
            int recId = DataInputOutputUtil.readINT(input) + prevId;
            result[i] = new RootRecord(recId, myNames.valueOf(nameId));
            prevId = recId;
            prevNameId = nameId;
          }
          return result;
        }
        finally {
          input.close();
        }
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
      return new RootRecord[0];
    }
  }

  private static void saveNameIdSequenceWithDeltas(int[] names, int[] ids, DataOutputStream output) throws IOException {
    DataInputOutputUtil.writeINT(output, names.length);
    int prevId = 0;
    int prevNameId = 0;
    for (int i = 0; i < names.length; i++) {
      DataInputOutputUtil.writeINT(output, names[i] - prevNameId);
      DataInputOutputUtil.writeINT(output, ids[i] - prevId);
      prevId = ids[i];
      prevNameId = names[i];
    }
  }

  @Override
  public int findRootRecord(@NotNull String rootUrl) {
    w.lock();

    try {
      markDirty();
      if (ourStoreRootsSeparately) {
        try {
          try (LineNumberReader stream = new LineNumberReader(new BufferedReader(new InputStreamReader(new FileInputStream(myRootsFile))))) {
            String str;
            while((str = stream.readLine()) != null) {
              int index = str.indexOf(' ');

              if (str.substring(index + 1).equals(rootUrl)) {
                return Integer.parseInt(str.substring(0, index));
              }
            }
          }
        } catch (FileNotFoundException ignored) {}
        try (Writer stream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(myRootsFile, true)))) {
          int id = createRecord();
          stream.write(id + " " + rootUrl + "\n");
          return id;
        }
      }

      final int root = myNames.enumerate(rootUrl);

      final DataInputStream input = readAttributeNoLock(ROOT_RECORD_ID, ourChildrenAttr);
      int[] names = ArrayUtil.EMPTY_INT_ARRAY;
      int[] ids = ArrayUtil.EMPTY_INT_ARRAY;

      if (input != null) {
        try {
          final int count = DataInputOutputUtil.readINT(input);
          names = ArrayUtil.newIntArray(count);
          ids = ArrayUtil.newIntArray(count);
          int prevId = 0;
          int prevNameId = 0;

          for (int i = 0; i < count; i++) {
            final int name = DataInputOutputUtil.readINT(input) + prevNameId;
            final int id = DataInputOutputUtil.readINT(input) + prevId;
            if (name == root) {
              return id;
            }

            prevNameId = names[i] = name;
            prevId = ids[i] = id;
          }
        }
        finally {
          input.close();
        }
      }

      int id;
      try (DataOutputStream output = writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        id = createRecord();

        int index = Arrays.binarySearch(ids, id);
        ids = ArrayUtil.insert(ids, -index - 1, id);
        names = ArrayUtil.insert(names, -index - 1, root);

        saveNameIdSequenceWithDeltas(names, ids, output);
      }

      return id;
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
    return -1;
  }

  @Override
  public void deleteRootRecord(int id) {
    w.lock();

    try {
      markDirty();
      if (ourStoreRootsSeparately) {
        List<String> rootsThatLeft = new ArrayList<>();
        try {
          try (LineNumberReader stream = new LineNumberReader(new BufferedReader(new InputStreamReader(new FileInputStream(myRootsFile))))) {
            String str;
            while((str = stream.readLine()) != null) {
              int index = str.indexOf(' ');
              int rootId = Integer.parseInt(str.substring(0, index));
              if (rootId != id) {
                rootsThatLeft.add(str);
              }
            }
          }
        } catch (FileNotFoundException ignored) {}

        try (Writer stream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(myRootsFile)))) {
          for(String line:rootsThatLeft) {
            stream.write(line);
            stream.write("\n");
          }
        }
        return;
      }

      final DataInputStream input = readAttributeNoLock(ROOT_RECORD_ID, ourChildrenAttr);
      assert input != null;
      int[] names;
      int[] ids;
      try {
        int count = DataInputOutputUtil.readINT(input);

        names = ArrayUtil.newIntArray(count);
        ids = ArrayUtil.newIntArray(count);
        int prevId = 0;
        int prevNameId = 0;
        for (int i = 0; i < count; i++) {
          names[i] = DataInputOutputUtil.readINT(input) + prevNameId;
          ids[i] = DataInputOutputUtil.readINT(input) + prevId;
          prevId = ids[i];
          prevNameId = names[i];
        }
      }
      finally {
        input.close();
      }

      final int index = ArrayUtil.find(ids, id);
      assert index >= 0;

      names = ArrayUtil.remove(names, index);
      ids = ArrayUtil.remove(ids, index);

      try (DataOutputStream output = writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        saveNameIdSequenceWithDeltas(names, ids, output);
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  @NotNull
  public int[] list(int id) {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttributeNoLock(id, ourChildrenAttr);
        if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;

        final int count = DataInputOutputUtil.readINT(input);
        final int[] result = ArrayUtil.newIntArray(count);
        int prevId = id;
        for (int i = 0; i < count; i++) {
          prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId;
        }
        input.close();
        return result;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
  }

  @Override
  @NotNull
  public NameId[] listAll(int parentId) {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttributeNoLock(parentId, ourChildrenAttr);
        if (input == null) return NameId.EMPTY_ARRAY;

        int count = DataInputOutputUtil.readINT(input);
        NameId[] result = count == 0 ? NameId.EMPTY_ARRAY : new NameId[count];
        int prevId = parentId;
        for (int i = 0; i < count; i++) {
          int id = DataInputOutputUtil.readINT(input) + prevId;
          prevId = id;
          int nameId = getNameId(id);
          result[i] = new NameId(id, nameId, myNameCache.getVFileName(nameId));
        }
        input.close();
        return result;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
      return NameId.EMPTY_ARRAY;
    }
  }

  @Override
  public boolean wereChildrenAccessed(int id) {
    try {
      r.lock();
      try {
        return findAttributePage(id, ourChildrenAttr, false) != 0;
      } finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return false;
  }

  @Override
  public void updateList(int id, @NotNull int[] childIds) {
    Arrays.sort(childIds);
    w.lock();
    try {
      markDirty();
      try (DataOutputStream record = writeAttribute(id, ourChildrenAttr)) {
        DataInputOutputUtil.writeINT(record, childIds.length);

        int prevId = id;
        for (int childId : childIds) {
          assert childId > 0 : childId;
          if (childId == id) {
            LOG.error("Cyclic parent child relations");
          }
          else {
            int delta = childId - prevId;
            DataInputOutputUtil.writeINT(record, delta);
            prevId = childId;
          }
        }
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  private void incModCount(int id) {
    incLocalModCount();
    final int count = getModCount() + 1;
    getRecords().putInt(HEADER_GLOBAL_MOD_COUNT_OFFSET, count);

    setModCount(id, count);
  }

  private void incLocalModCount() {
    markDirty();
    myLocalModificationCount++;
  }

  @Override
  public int getLocalModCount() {
    return myLocalModificationCount; // This is volatile, only modified under Application.runWriteAction() lock.
  }

  @Override
  public int getModCount() {
    r.lock();
    try {
      return getRecords().getInt(HEADER_GLOBAL_MOD_COUNT_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public int getParent(int id) {
    try {
      r.lock();
      try {
        final int parentId = getRecordInt(id, PARENT_OFFSET);
        if (parentId == id) {
          LOG.error("Cyclic parent child relations in the database. id = " + id);
          return 0;
        }

        return parentId;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return -1;
  }

  // returns id, parent(id), parent(parent(id)), ...  (already cached id or rootId)
  @Override
  @NotNull
  public TIntArrayList getParents(int id, @NotNull IntPredicate cached) {
    TIntArrayList result = new TIntArrayList(10);
    r.lock();
    try {
      int parentId;
      do {
        result.add(id);
        if (cached.test(id)) {
          break;
        }
        parentId = getRecordInt(id, PARENT_OFFSET);
        if (parentId == id || result.size() % 128 == 0 && result.contains(parentId)) {
          LOG.error("Cyclic parent child relations in the database. id = " + parentId);
          return result;
        }
        id = parentId;
      } while (parentId != 0);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      r.unlock();
    }
    return result;
  }

  @Override
  public void setParent(int id, int parentId) {
    if (id == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    w.lock();
    try {
      incModCount(id);
      putRecordInt(id, PARENT_OFFSET, parentId);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public int getNameId(int id) {
    try {
      r.lock();
      try {
        return getRecordInt(id, NAME_OFFSET);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return -1;
  }

  @Override
  public int getNameId(String name) {
    try {
      r.lock();
      try {
        return myNames.enumerate(name);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return -1;
  }

  @Override
  public String getName(int id) {
    return getNameSequence(id).toString();
  }

  @Override
  @NotNull
  public CharSequence getNameSequence(int id) {
    try {
      r.lock();
      try {
        final int nameId = getRecordInt(id, NAME_OFFSET);
        return nameId == 0 ? "" : myNameCache.getVFileName(nameId);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
      return "";
    }
  }

  @Override
  public void setName(int id, @NotNull String name) {
    w.lock();
    try {
      incModCount(id);
      int nameId = myNames.enumerate(name);
      putRecordInt(id, NAME_OFFSET, nameId);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public int getFlags(int id) {
    r.lock();
    try {
      return getRecordInt(id, FLAGS_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public void setFlags(int id, int flags, final boolean markAsChange) {
    w.lock();
    try {
      if (markAsChange) {
        incModCount(id);
      }
      putRecordInt(id, FLAGS_OFFSET, flags);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public long getLength(int id) {
    r.lock();
    try {
      return getRecords().getLong(getOffset(id, LENGTH_OFFSET));
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public void setLength(int id, long len) {
    w.lock();
    try {
      ResizeableMappedFile records = getRecords();
      int lengthOffset = getOffset(id, LENGTH_OFFSET);
      if (records.getLong(lengthOffset) != len) {
        incModCount(id);
        records.putLong(lengthOffset, len);
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public long getTimestamp(int id) {
    r.lock();
    try {
      return getRecords().getLong(getOffset(id, TIMESTAMP_OFFSET));
    }
    finally {
      r.unlock();
    }
  }

  @Override
  public void setTimestamp(int id, long value) {
    w.lock();
    try {
      int timeStampOffset = getOffset(id, TIMESTAMP_OFFSET);
      ResizeableMappedFile records = getRecords();
      if (records.getLong(timeStampOffset) != value) {
        incModCount(id);
        records.putLong(timeStampOffset, value);
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public int getModCount(int id) {
    r.lock();
    try {
      return getRecordInt(id, MOD_COUNT_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  private void setModCount(int id, int value) {
    putRecordInt(id, MOD_COUNT_OFFSET, value);
  }

  private int getContentRecordId(int fileId) {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  private void setContentRecordId(int id, int value) {
    putRecordInt(id, CONTENT_OFFSET, value);
  }

  private int getAttributeRecordId(int id) {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  private void setAttributeRecordId(int id, int value) {
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  private int getRecordInt(int id, int offset) {
    return getRecords().getInt(getOffset(id, offset));
  }

  private void putRecordInt(int id, int offset, int value) {
    getRecords().putInt(getOffset(id, offset), value);
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  @Override
  @Nullable
  public DataInputStream readContent(int fileId) {
    try {
      r.lock();
      int page;
      try {
        checkFileIsValid(fileId);

        page = getContentRecordId(fileId);
        if (page == 0) return null;
      }
      finally {
        r.unlock();
      }
      return doReadContentById(page);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return null;
  }

  @Override
  @Nullable
  public DataInputStream readContentById(int contentId) {
    try {
      return doReadContentById(contentId);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return null;
  }

  private DataInputStream doReadContentById(int contentId) throws IOException {
    DataInputStream stream = getContentStorage().readStream(contentId);
    if (useSnappyForCompression) {
      byte[] bytes = CompressionUtil.readCompressed(stream);
      stream = new DataInputStream(new ByteArrayInputStream(bytes));
    }

    return stream;
  }

  @Override
  @Nullable
  public DataInputStream readAttribute(int fileId, FileAttribute att) {
    try {
      r.lock();
      try {
        DataInputStream stream = readAttributeNoLock(fileId, att);
        if (stream != null && att.isVersioned()) {
          try {
            int actualVersion = DataInputOutputUtil.readINT(stream);
            if (actualVersion != att.getVersion()) {
              stream.close();
              return null;
            }
          }
          catch (IOException e) {
            stream.close();
            return null;
          }
        }
        return stream;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return null;
  }

  // should be called under r or w lock
  @Nullable
  private DataInputStream readAttributeNoLock(int fileId, FileAttribute attribute) throws IOException {
    checkFileIsValid(fileId);

    int recordId = getAttributeRecordId(fileId);
    if (recordId == 0) return null;
    int encodedAttrId = getAttributeId(attribute.getId());

    Storage storage = getAttributesStorage();

    int page = 0;

    try (DataInputStream attrRefs = storage.readStream(recordId)) {
      if (bulkAttrReadSupport) skipRecordHeader(attrRefs, RESERVED_ATTR_ID, fileId);

      while (attrRefs.available() > 0) {
        final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
        final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

        if (attIdOnPage != encodedAttrId) {
          if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
            attrRefs.skipBytes(attrAddressOrSize);
          }
        }
        else {
          if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
            byte[] b = new byte[attrAddressOrSize];
            attrRefs.readFully(b);
            return new DataInputStream(new ByteArrayInputStream(b));
          }
          page = inlineAttributes ? attrAddressOrSize - MAX_SMALL_ATTR_SIZE : attrAddressOrSize;
          break;
        }
      }
    }

    if (page == 0) {
      return null;
    }
    DataInputStream stream = getAttributesStorage().readStream(page);
    if (bulkAttrReadSupport) skipRecordHeader(stream, encodedAttrId, fileId);
    return stream;
  }

  // Vfs small attrs: store inline:
  // file's AttrId -> [size, capacity] attr record (RESERVED_ATTR_ID fileId)? (attrId ((smallAttrSize smallAttrData) | (attr record)) )
  // other attr record: (AttrId, fileId) ? attrData
  private static final int MAX_SMALL_ATTR_SIZE = 64;

  private int findAttributePage(int fileId, FileAttribute attr, boolean toWrite) throws IOException {
    checkFileIsValid(fileId);

    int recordId = getAttributeRecordId(fileId);
    int encodedAttrId = getAttributeId(attr.getId());
    boolean directoryRecord = false;

    Storage storage = getAttributesStorage();

    if (recordId == 0) {
      if (!toWrite) return 0;

      recordId = storage.createNewRecord();
      setAttributeRecordId(fileId, recordId);
      directoryRecord = true;
    }
    else {
      try (DataInputStream attrRefs = storage.readStream(recordId)) {
        if (bulkAttrReadSupport) skipRecordHeader(attrRefs, RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) {
            if (inlineAttributes) {
              return attrAddressOrSize < MAX_SMALL_ATTR_SIZE ? -recordId : attrAddressOrSize - MAX_SMALL_ATTR_SIZE;
            }
            else {
              return attrAddressOrSize;
            }
          }
          else {
            if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              attrRefs.skipBytes(attrAddressOrSize);
            }
          }
        }
      }
    }

    if (toWrite) {
      Storage.AppenderStream appender = storage.appendStream(recordId);
      if (bulkAttrReadSupport) {
        if (directoryRecord) {
          DataInputOutputUtil.writeINT(appender, RESERVED_ATTR_ID);
          DataInputOutputUtil.writeINT(appender, fileId);
        }
      }

      DataInputOutputUtil.writeINT(appender, encodedAttrId);
      int attrAddress = storage.createNewRecord();
      DataInputOutputUtil.writeINT(appender, inlineAttributes ? attrAddress + MAX_SMALL_ATTR_SIZE : attrAddress);
      REASONABLY_SMALL.myAttrPageRequested = true;
      try {
        appender.close();
      } finally {
        REASONABLY_SMALL.myAttrPageRequested = false;
      }
      return attrAddress;
    }

    return 0;
  }

  private static void skipRecordHeader(DataInputStream refs, int expectedRecordTag, int expectedFileId) throws IOException {
    int attId = DataInputOutputUtil.readINT(refs);// attrId
    assert attId == expectedRecordTag || expectedRecordTag == 0;
    int fileId = DataInputOutputUtil.readINT(refs);// fileId
    assert expectedFileId == fileId || expectedFileId == 0;
  }

  private static void writeRecordHeader(int recordTag, int fileId, DataOutputStream appender) throws IOException {
    DataInputOutputUtil.writeINT(appender, recordTag);
    DataInputOutputUtil.writeINT(appender, fileId);
  }

  private void checkFileIsValid(int fileId) {
    assert fileId > 0 : fileId;
    // TODO: This assertion is a bit timey, will remove when bug is caught.
    if (!lazyVfsDataCleaning) {
      assert !BitUtil.isSet(getFlags(fileId), FREE_RECORD_FLAG) : "Accessing attribute of a deleted page: " + fileId + ":" + getName(fileId);
    }
  }

  @Override
  public int acquireFileContent(int fileId) {
    w.lock();
    try {
      int record = getContentRecordId(fileId);
      if (record > 0) getContentStorage().acquireRecord(record);
      return record;
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
    return -1;
  }

  @Override
  public void releaseContent(int contentId) {
    w.lock();
    try {
      RefCountingStorage contentStorage = getContentStorage();
      if (weHaveContentHashes) {
        contentStorage.releaseRecord(contentId, false);
      } else {
        contentStorage.releaseRecord(contentId);
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
  }

  @Override
  public int getContentId(int fileId) {
    try {
      r.lock();
      try {
        return getContentRecordId(fileId);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    return -1;
  }

  @Override
  @NotNull
  public DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new ContentOutputStream(fileId, readOnly);
  }

  private static final MessageDigest myDigest = ContentHashesUtil.createHashDigest();

  @Override
  public void writeContent(int fileId, ByteSequence bytes, boolean readOnly) {
    try {
      writeBytes(fileId, bytes, readOnly);
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
  }

  @Override
  public int storeUnlinkedContent(byte[] bytes) {
    w.lock();
    try {
      int recordId;

      if (weHaveContentHashes) {
        recordId = findOrCreateContentRecord(bytes, 0, bytes.length);
        if (recordId > 0) return recordId;
        recordId = -recordId;
      } else {
        recordId = getContentStorage().acquireNewRecord();
      }
      AbstractStorage.StorageDataOutput output = getContentStorage().writeStream(recordId, true);
      output.write(bytes);
      output.close();
      return recordId;
    }
    catch (IOException e) {
      requestRebuild(e);
    }
    finally {
      w.unlock();
    }
    return -1;
  }

  @Override
  @NotNull
  public DataOutputStream writeAttribute(final int fileId, @NotNull FileAttribute att) {
    DataOutputStream stream = new AttributeOutputStream(fileId, att);
    if (att.isVersioned()) {
      try {
        DataInputOutputUtil.writeINT(stream, att.getVersion());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return stream;
  }

  @Override
  public void writeBytes(int fileId, ByteSequence bytes, boolean preferFixedSize) throws IOException {
    RefCountingStorage contentStorage = getContentStorage();
    w.lock();
    try {
      checkFileIsValid(fileId);

      int page;
      final boolean fixedSize;
      if (weHaveContentHashes) {
        page = findOrCreateContentRecord(bytes.getBytes(), bytes.getOffset(), bytes.getLength());

        if (page < 0 || getContentId(fileId) != page) {
          incModCount(fileId);
          setContentRecordId(fileId, page > 0 ? page : -page);
        }

        setContentRecordId(fileId, page > 0 ? page : -page);

        if (page > 0) return;
        page = -page;
        fixedSize = true;
      }
      else {
        incModCount(fileId);
        page = getContentRecordId(fileId);
        if (page == 0 || contentStorage.getRefCount(page) > 1) {
          page = contentStorage.acquireNewRecord();
          setContentRecordId(fileId, page);
        }
        fixedSize = preferFixedSize;
      }

      if (useSnappyForCompression) {
        BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(out);
        byte[] rawBytes = bytes.getBytes();
        if (bytes.getOffset() != 0) {
          rawBytes = new byte[bytes.getLength()];
          System.arraycopy(bytes.getBytes(), bytes.getOffset(), rawBytes, 0, bytes.getLength());
        }
        CompressionUtil.writeCompressed(outputStream, rawBytes, bytes.getLength());
        outputStream.close();
        bytes = new ByteSequence(out.getInternalBuffer(), 0, out.size());
      }
      contentStorage.writeBytes(page, bytes, fixedSize);
    }
    finally {
      w.unlock();
    }
  }

  private class ContentOutputStream extends DataOutputStream {
    final int myFileId;
    final boolean myFixedSize;

    ContentOutputStream(final int fileId, boolean fixedSize) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myFixedSize = fixedSize;
    }

    @Override
    public void close() throws IOException {
      super.close();

      try {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
        FSRecords.this.writeBytes(myFileId, new ByteSequence(_out.getInternalBuffer(), 0, _out.size()), myFixedSize);
      }
      catch (Throwable e) {
        requestRebuild(e);
      }
    }
  }

  private static final boolean DO_HARD_CONSISTENCY_CHECK = false;
  private static final boolean DUMP_STATISTICS = weHaveContentHashes;  // TODO: remove once not needed
  //statistics:
  private long totalContents;
  private long totalReuses;
  private long time;
  private int contents;
  private int reuses;

  private int findOrCreateContentRecord(byte[] bytes, int offset, int length) throws IOException {
    assert weHaveContentHashes;

    long started = DUMP_STATISTICS ? System.nanoTime():0;
    myDigest.reset();
    myDigest.update(String.valueOf(length - offset).getBytes(Charset.defaultCharset()));
    myDigest.update("\0".getBytes(Charset.defaultCharset()));
    myDigest.update(bytes, offset, length);
    byte[] digest = myDigest.digest();
    long done = DUMP_STATISTICS ? System.nanoTime() - started : 0;
    time += done;

    ++contents;
    totalContents += length;

    if (DUMP_STATISTICS && (contents & 0x3FFF) == 0) {
      LOG.info("Contents:" + contents + " of " + totalContents + ", reuses:" + reuses + " of " + totalReuses + " for " + time / 1000000);
    }
    PersistentBTreeEnumerator<byte[]> hashesEnumerator = getContentHashesEnumerator();
    final int largestId = hashesEnumerator.getLargestId();
    int page = hashesEnumerator.enumerate(digest);

    if (page <= largestId) {
      ++reuses;
      getContentStorage().acquireRecord(page);
      totalReuses += length;

      if (DO_HARD_CONSISTENCY_CHECK) {
        DataInputStream stream = doReadContentById(page);
        int i = offset;
        for(int c = 0; c < length; ++c) {
          if (stream.available() == 0) {
            assert false;
          }
          if (bytes[i++] != stream.readByte()) {
            assert false;
          }
        }
        if (stream.available() > 0) {
          assert false;
        }
      }
      return page;
    } else {
      int newRecord = getContentStorage().acquireNewRecord();
      if (page != newRecord) {
        assert false:"Unexpected content storage modification";
      }
      if (DO_HARD_CONSISTENCY_CHECK) {
        if (hashesEnumerator.enumerate(digest) != page) {
          assert false;
        }

        byte[] bytes1 = hashesEnumerator.valueOf(page);
        if (!Arrays.equals(digest, bytes1)) {
          assert false;
        }
      }
      return -page;
    }
  }

  private class AttributeOutputStream extends DataOutputStream {
    private final FileAttribute myAttribute;
    private final int myFileId;

    private AttributeOutputStream(final int fileId, @NotNull FileAttribute attribute) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myAttribute = attribute;
    }

    @Override
    public void close() throws IOException {
      super.close();

      try {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

        if (inlineAttributes && _out.size() < MAX_SMALL_ATTR_SIZE) {
          w.lock();
          try {
            rewriteDirectoryRecordWithAttrContent(_out);
            incLocalModCount();
          }
          finally {
            w.unlock();
          }
        }
        else {
          w.lock();
          try {
            incLocalModCount();
            int page = findAttributePage(myFileId, myAttribute, true);
            if (inlineAttributes && page < 0) {
              rewriteDirectoryRecordWithAttrContent(new BufferExposingByteArrayOutputStream());
              page = findAttributePage(myFileId, myAttribute, true);
            }

            if (bulkAttrReadSupport) {
              BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
              out = stream;
              writeRecordHeader(getAttributeId(myAttribute.getId()), myFileId, this);
              write(_out.getInternalBuffer(), 0, _out.size());
              getAttributesStorage().writeBytes(page, new ByteSequence(stream.getInternalBuffer(), 0, stream.size()), myAttribute.isFixedSize());
            }
            else {
              getAttributesStorage().writeBytes(page, new ByteSequence(_out.getInternalBuffer(), 0, _out.size()), myAttribute.isFixedSize());
            }
          }
          finally {
            w.unlock();
          }
        }
      }
      catch (Throwable e) {
        requestRebuild(e);
      }
    }

    void rewriteDirectoryRecordWithAttrContent(BufferExposingByteArrayOutputStream _out) throws IOException {
      int recordId = getAttributeRecordId(myFileId);
      assert inlineAttributes;
      int encodedAttrId = getAttributeId(myAttribute.getId());

      Storage storage = getAttributesStorage();
      BufferExposingByteArrayOutputStream unchangedPreviousDirectoryStream = null;
      boolean directoryRecord = false;


      if (recordId == 0) {
        recordId = storage.createNewRecord();
        setAttributeRecordId(myFileId, recordId);
        directoryRecord = true;
      }
      else {
        DataInputStream attrRefs = storage.readStream(recordId);

        DataOutputStream dataStream = null;

        try {
          final int remainingAtStart = attrRefs.available();
          if (bulkAttrReadSupport) {
            unchangedPreviousDirectoryStream = new BufferExposingByteArrayOutputStream();
            dataStream = new DataOutputStream(unchangedPreviousDirectoryStream);
            int attId = DataInputOutputUtil.readINT(attrRefs);
            assert attId == RESERVED_ATTR_ID;
            int fileId = DataInputOutputUtil.readINT(attrRefs);
            assert myFileId == fileId;

            writeRecordHeader(attId, fileId, dataStream);
          }
          while (attrRefs.available() > 0) {
            final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
            final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

            if (attIdOnPage != encodedAttrId) {
              if (dataStream == null) {
                unchangedPreviousDirectoryStream = new BufferExposingByteArrayOutputStream();
                dataStream = new DataOutputStream(unchangedPreviousDirectoryStream);
              }
              DataInputOutputUtil.writeINT(dataStream, attIdOnPage);
              DataInputOutputUtil.writeINT(dataStream, attrAddressOrSize);

              if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                byte[] b = new byte[attrAddressOrSize];
                attrRefs.readFully(b);
                dataStream.write(b);
              }
            }
            else {
              if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                if (_out.size() == attrAddressOrSize) {
                  // update inplace when new attr has the same size
                  int remaining = attrRefs.available();
                  storage.replaceBytes(recordId, remainingAtStart - remaining, new ByteSequence(_out.getInternalBuffer(), 0, _out.size()));
                  return;
                }
                attrRefs.skipBytes(attrAddressOrSize);
              }
            }
          }
        }
        finally {
          attrRefs.close();
          if (dataStream != null) dataStream.close();
        }
      }

      AbstractStorage.StorageDataOutput directoryStream = storage.writeStream(recordId);
      if (directoryRecord) {
        if (bulkAttrReadSupport) writeRecordHeader(RESERVED_ATTR_ID, myFileId, directoryStream);
      }
      if(unchangedPreviousDirectoryStream != null) {
        directoryStream.write(unchangedPreviousDirectoryStream.getInternalBuffer(), 0, unchangedPreviousDirectoryStream.size());
      }
      if (_out.size() > 0) {
        DataInputOutputUtil.writeINT(directoryStream, encodedAttrId);
        DataInputOutputUtil.writeINT(directoryStream, _out.size());
        directoryStream.write(_out.getInternalBuffer(), 0, _out.size());
      }

      directoryStream.close();
    }
  }

  @Override
  public void dispose() {
    w.lock();
    try {
      force();
      closeFiles();
    }
    catch (Throwable e) {
      requestRebuild(e);
    }
    finally {
      myIsDisposed = true;
      w.unlock();
    }
  }

  @Override
  public void invalidateCaches() {
    createBrokenMarkerFile(null);
  }

  void checkSanity() {
    long t = System.currentTimeMillis();

    r.lock();
    try {
      final int fileLength = length();
      assert fileLength % RECORD_SIZE == 0;
      int recordCount = fileLength / RECORD_SIZE;

      IntArrayList usedAttributeRecordIds = new IntArrayList();
      IntArrayList validAttributeIds = new IntArrayList();
      for (int id = 2; id < recordCount; id++) {
        int flags = getFlags(id);
        LOG.assertTrue((flags & ~ALL_VALID_FLAGS) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);
        if (BitUtil.isSet(flags, FREE_RECORD_FLAG)) {
          LOG.assertTrue(myFreeRecords.contains(id), "Record, marked free, not in free list: " + id);
        }
        else {
          LOG.assertTrue(!myFreeRecords.contains(id), "Record, not marked free, in free list: " + id);
          checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds);
        }
      }
    }
    finally {
      r.unlock();
    }

    t = System.currentTimeMillis() - t;
    LOG.info("Sanity check took " + t + " ms");
  }

  private void checkRecordSanity(final int id, final int recordCount, final IntArrayList usedAttributeRecordIds,
                                 final IntArrayList validAttributeIds) {
    int parentId = getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && getParent(parentId) > 0) {
      int parentFlags = getFlags(parentId);
      assert !BitUtil.isSet(parentFlags, FREE_RECORD_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.IS_DIRECTORY_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    String name = getName(id);
    LOG.assertTrue(parentId == 0 || !name.isEmpty(), "File with empty name found under " + getName(parentId) + ", id=" + id);

    checkContentsStorageSanity(id);
    checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds);

    long length = getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  private void checkContentsStorageSanity(int id) {
    int recordId = getContentRecordId(id);
    assert recordId >= 0;
    if (recordId > 0) {
      getContentStorage().checkSanity(recordId);
    }
  }

  private void checkAttributesStorageSanity(int id, IntArrayList usedAttributeRecordIds, IntArrayList validAttributeIds) {
    int attributeRecordId = getAttributeRecordId(id);

    assert attributeRecordId >= 0;
    if (attributeRecordId > 0) {
      try {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
      catch (IOException ex) {
        requestRebuild(ex);
      }
    }
  }

  private void checkAttributesSanity(final int attributeRecordId, final IntArrayList usedAttributeRecordIds,
                                     final IntArrayList validAttributeIds) throws IOException {
    assert !usedAttributeRecordIds.contains(attributeRecordId);
    usedAttributeRecordIds.add(attributeRecordId);

    try (DataInputStream dataInputStream = getAttributesStorage().readStream(attributeRecordId)) {
      if (bulkAttrReadSupport) skipRecordHeader(dataInputStream, 0, 0);

      while (dataInputStream.available() > 0) {
        int attId = DataInputOutputUtil.readINT(dataInputStream);

        if (!validAttributeIds.contains(attId)) {
          //assert !getNames().valueOf(attId).isEmpty();
          validAttributeIds.add(attId);
        }

        int attDataRecordIdOrSize = DataInputOutputUtil.readINT(dataInputStream);

        if (inlineAttributes) {
          if (attDataRecordIdOrSize < MAX_SMALL_ATTR_SIZE) {
            dataInputStream.skipBytes(attDataRecordIdOrSize);
            continue;
          }
          else attDataRecordIdOrSize -= MAX_SMALL_ATTR_SIZE;
        }
        assert !usedAttributeRecordIds.contains(attDataRecordIdOrSize);
        usedAttributeRecordIds.add(attDataRecordIdOrSize);

        getAttributesStorage().checkSanity(attDataRecordIdOrSize);
      }
    }
  }

  /*
  public interface BulkAttrReadCallback {
    boolean accepts(int fileId);
    boolean execute(int fileId, DataInputStream is);
  }

  // custom DataInput implementation instead of DataInputStream (without extra allocations) (api change)
  // store each attr in separate file: pro: read only affected data, easy versioning

  public static void readAttributeInBulk(FileAttribute attr, BulkAttrReadCallback callback) throws IOException {
    String attrId = attr.getId();
    int encodedAttrId = DbConnection.getAttributeId(attrId);
    synchronized (attrId) {
      Storage storage = getAttributesStorage();
      RecordIterator recordIterator = storage.recordIterator();
      while (recordIterator.hasNextRecordId()) {
        int recordId = recordIterator.nextRecordId();
        DataInputStream stream = storage.readStream(recordId);

        int currentAttrId = DataInputOutputUtil.readINT(stream);
        int fileId = DataInputOutputUtil.readINT(stream);
        if (!callback.accepts(fileId)) continue;

        if (currentAttrId == DbConnection.RESERVED_ATTR_ID) {
          if (!inlineAttributes) continue;

          while(stream.available() > 0) {
            int directoryAttrId = DataInputOutputUtil.readINT(stream);
            int directoryAttrAddressOrSize = DataInputOutputUtil.readINT(stream);

            if (directoryAttrId != encodedAttrId) {
              if (directoryAttrAddressOrSize < MAX_SMALL_ATTR_SIZE) stream.skipBytes(directoryAttrAddressOrSize);
            } else {
              if (directoryAttrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                byte[] b = new byte[directoryAttrAddressOrSize];
                stream.readFully(b);
                DataInputStream inlineAttrStream = new DataInputStream(new ByteArrayInputStream(b));
                int version = DataInputOutputUtil.readINT(inlineAttrStream);
                if (version != attr.getVersion()) continue;
                boolean result = callback.execute(fileId, inlineAttrStream); // todo
                if (!result) break;
              }
            }
          }
        } else if (currentAttrId == encodedAttrId) {
          int version = DataInputOutputUtil.readINT(stream);
          if (version != attr.getVersion()) continue;

          boolean result = callback.execute(fileId, stream); // todo
          if (!result) break;
        }
      }
    }
  }*/
}
