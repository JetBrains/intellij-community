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

import com.intellij.openapi.Forceable;
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
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.io.IOUtil.deleteAllFilesStartingWith;

/**
 * @author max
 */
@SuppressWarnings({"PointlessArithmeticExpression", "HardCodedStringLiteral"})
public class FSRecords implements Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  public static final boolean weHaveContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  public static final boolean lazyVfsDataCleaning = SystemProperties.getBooleanProperty("idea.lazy.vfs.data.cleaning", true);
  public static final boolean backgroundVfsFlush = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);
  public static final boolean persistentAttributesList = SystemProperties.getBooleanProperty("idea.persistent.attr.list", true);
  private static final boolean inlineAttributes = SystemProperties.getBooleanProperty("idea.inline.vfs.attributes", true);
  public static final boolean bulkAttrReadSupport = SystemProperties.getBooleanProperty("idea.bulk.attr.read", false);
  public static final boolean useSnappyForCompression = SystemProperties.getBooleanProperty("idea.use.snappy.for.vfs", false);
  public static final boolean useSmallAttrTable = SystemProperties.getBooleanProperty("idea.use.small.attr.table.for.vfs", true);
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");

  private static final int VERSION = 21 + (weHaveContentHashes ? 0x10:0) + (IOUtil.ourByteBuffersUseNativeByteOrder ? 0x37:0) +
                                     (persistentAttributesList ? 31 : 0) + (bulkAttrReadSupport ? 0x27:0) + (inlineAttributes ? 0x31 : 0) +
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

  private static final ReentrantReadWriteLock.ReadLock r;
  private static final ReentrantReadWriteLock.WriteLock w;

  private static volatile int ourLocalModificationCount = 0;
  private static volatile boolean ourIsDisposed;

  private static final int FREE_RECORD_FLAG = 0x100;
  private static final int ALL_VALID_FLAGS = PersistentFS.ALL_VALID_FLAGS | FREE_RECORD_FLAG;

  static {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= RECORD_SIZE;

    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    r = lock.readLock();
    w = lock.writeLock();
  }

  static void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
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
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static void requestVfsRebuild(Throwable e) {
    //noinspection ThrowableResultOfMethodCallIgnored
    DbConnection.handleError(e);
  }

  static File basePath() {
    return new File(DbConnection.getCachesDir());
  }

  public static class DbConnection {
    private static boolean ourInitialized;
    private static final ConcurrentMap<String, Integer> myAttributeIds = ContainerUtil.newConcurrentMap();

    private static PersistentStringEnumerator myNames;
    private static Storage myAttributes;
    private static RefCountingStorage myContents;
    private static ResizeableMappedFile myRecords;
    private static PersistentBTreeEnumerator<byte[]> myContentHashesEnumerator;
    private static final VfsDependentEnum<String> myAttributesList = new VfsDependentEnum<String>("attrib", EnumeratorStringDescriptor.INSTANCE, 1);
    private static final TIntArrayList myFreeRecords = new TIntArrayList();

    private static boolean myDirty = false;
    private static ScheduledFuture<?> myFlushingFuture;
    private static boolean myCorrupted = false;

    private static final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();


    public static void connect() {
      w.lock();
      try {
        if (!ourInitialized) {
          init();
          setupFlushing();
          ourInitialized = true;
        }
      }
      finally {
        w.unlock();
      }
    }

    private static void scanFreeRecords() {
      final int filelength = (int)myRecords.length();
      LOG.assertTrue(filelength % RECORD_SIZE == 0, "invalid file size: " + filelength);

      int count = filelength / RECORD_SIZE;
      for (int n = 2; n < count; n++) {
        if (BitUtil.isSet(getFlags(n), FREE_RECORD_FLAG)) {
          myFreeRecords.add(n);
        }
      }
    }

    static int getFreeRecord() {
      if (myFreeRecords.isEmpty()) return 0;
      return myFreeRecords.remove(myFreeRecords.size() - 1);
    }

    private static void createBrokenMarkerFile(@Nullable Throwable reason) {
      final File brokenMarker = getCorruptionMarkerFile();

      try {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        try {
          new Exception().printStackTrace(stream);
          if (reason != null) {
            stream.print("\nReason:\n");
            reason.printStackTrace(stream);
          }
        }
        finally {
          stream.close();
        }
        LOG.info("Creating VFS corruption marker; Trace=\n" + out.toString());

        final FileWriter writer = new FileWriter(brokenMarker);
        try {
          writer.write("These files are corrupted and must be rebuilt from the scratch on next startup");
        }
        finally {
          writer.close();
        }
      }
      catch (IOException e) {
        // No luck.
      }
    }

    private static File getCorruptionMarkerFile() {
      return new File(basePath(), "corruption.marker");
    }

    private static void init() {
      final File basePath = basePath().getAbsoluteFile();
      basePath.mkdirs();

      final File namesFile = new File(basePath, "names" + VFS_FILES_EXTENSION);
      final File attributesFile = new File(basePath, "attrib" + VFS_FILES_EXTENSION);
      final File contentsFile = new File(basePath, "content" + VFS_FILES_EXTENSION);
      final File contentsHashesFile = new File(basePath, "contentHashes" + VFS_FILES_EXTENSION);
      final File recordsFile = new File(basePath, "records" + VFS_FILES_EXTENSION);

      final File vfsDependentEnumBaseFile = VfsDependentEnum.getBaseFile();

      if (!namesFile.exists()) {
        invalidateIndex("'" + namesFile.getPath() + "' does not exist");
      }

      try {
        if (getCorruptionMarkerFile().exists()) {
          invalidateIndex("corruption marker found");
          throw new IOException("Corruption marker file found");
        }

        PagedFileStorage.StorageLockContext storageLockContext = new PagedFileStorage.StorageLockContext(false);
        myNames = new PersistentStringEnumerator(namesFile, storageLockContext);

        myAttributes = new Storage(attributesFile.getPath(), REASONABLY_SMALL) {
          @Override
          protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
            return inlineAttributes && useSmallAttrTable ? new CompactRecordsTable(recordsFile, pool, false) : super.createRecordsTable(pool, recordsFile);
          }
        };
        myContents = new RefCountingStorage(contentsFile.getPath(), CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH, useSnappyForCompression) {
          @NotNull
          @Override
          protected ExecutorService createExecutor() {
            return AppExecutorUtil.createBoundedApplicationPoolExecutor(1);
          }
        }; // sources usually zipped with 4x ratio
        myContentHashesEnumerator = weHaveContentHashes ? new ContentHashesUtil.HashEnumerator(contentsHashesFile, storageLockContext): null;
        boolean aligned = PagedFileStorage.BUFFER_SIZE % RECORD_SIZE == 0;
        assert aligned; // for performance
        myRecords = new ResizeableMappedFile(recordsFile, 20 * 1024, storageLockContext,
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
          deleted &= deleteAllFilesStartingWith(namesFile);
          deleted &= AbstractStorage.deleteFiles(attributesFile.getPath());
          deleted &= AbstractStorage.deleteFiles(contentsFile.getPath());
          deleted &= deleteAllFilesStartingWith(contentsHashesFile);
          deleted &= deleteAllFilesStartingWith(recordsFile);
          deleted &= deleteAllFilesStartingWith(vfsDependentEnumBaseFile);

          if (!deleted) {
            throw new IOException("Cannot delete filesystem storage files");
          }
        }
        catch (final IOException e1) {
          final Runnable warnAndShutdown = new Runnable() {
            @Override
            public void run() {
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
            }
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

        init();
      }
    }

    private static void invalidateIndex(String reason) {
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

    private static String getCachesDir() {
      String dir = System.getProperty("caches_dir");
      return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
    }

    private static void markDirty() {
      if (!myDirty) {
        myDirty = true;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);
      }
    }

    private static void setupFlushing() {
      if (!backgroundVfsFlush)
        return;

      myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
        private int lastModCount;

        @Override
        public void run() {
          if (lastModCount == ourLocalModificationCount) {
            flushSome();
          }
          lastModCount = ourLocalModificationCount;
        }
      });
    }

    public static void force() {
      w.lock();
      try {
        if (myRecords != null) {
          markClean();
        }
        if (myNames != null) {
          myNames.force();
          myAttributes.force();
          myContents.force();
          if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
          myRecords.force();
        }
      }
      finally {
        w.unlock();
      }
    }

    public static void flushSome() {
      if (!isDirty() || HeavyProcessLatch.INSTANCE.isRunning()) return;

      r.lock();
      try {
        if (myFlushingFuture == null) {
          return; // avoid NPE when close has already taken place
        }
        myNames.force();

        final boolean attribsFlushed = myAttributes.flushSome();
        final boolean contentsFlushed = myContents.flushSome();
        if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
        if (attribsFlushed && contentsFlushed) {
          markClean();
          myRecords.force();
        }
      }
      finally {
        r.unlock();
      }
    }

    public static boolean isDirty() {
      return myDirty || myNames.isDirty() || myAttributes.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
             (myContentHashesEnumerator != null && myContentHashesEnumerator.isDirty());
    }


    private static int getVersion() {
      final int recordsVersion = myRecords.getInt(HEADER_VERSION_OFFSET);
      if (myAttributes.getVersion() != recordsVersion || myContents.getVersion() != recordsVersion) return -1;

      return recordsVersion;
    }

    public static long getTimestamp() {
      return myRecords.getLong(HEADER_TIMESTAMP_OFFSET);
    }

    private static void setCurrentVersion() {
      myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
      myRecords.putLong(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
      myAttributes.setVersion(VERSION);
      myContents.setVersion(VERSION);
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
    }

    static void cleanRecord(int id) {
      myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
    }

    public static PersistentStringEnumerator getNames() {
      return myNames;
    }

    private static void closeFiles() throws IOException {
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
      ourInitialized = false;
    }

    private static void markClean() {
      if (myDirty) {
        myDirty = false;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, myCorrupted ? CORRUPTED_MAGIC : SAFELY_CLOSED_MAGIC);
      }
    }

    private static final int RESERVED_ATTR_ID = bulkAttrReadSupport ? 1 : 0;
    private static final int FIRST_ATTR_ID_OFFSET = bulkAttrReadSupport ? RESERVED_ATTR_ID : 0;

    private static int getAttributeId(@NotNull String attId) throws IOException {
      if (persistentAttributesList) {
        return myAttributesList.getId(attId) + FIRST_ATTR_ID_OFFSET;
      }
      Integer integer = myAttributeIds.get(attId);
      if (integer != null) return integer.intValue();
      int enumeratedId = myNames.enumerate(attId);
      integer = myAttributeIds.putIfAbsent(attId, enumeratedId);
      return integer == null ? enumeratedId:  integer.intValue();
    }

    private static RuntimeException handleError(final Throwable e) {
      if (!ourIsDisposed) {
        // No need to forcibly mark VFS corrupted if it is already shut down
        if (!myCorrupted && w.tryLock()) { // avoid deadlock if r lock is occupied by current thread
          w.unlock();
          createBrokenMarkerFile(e);
          myCorrupted = true;
          force();
        }
      }

      return new RuntimeException(e);
    }

    private static class AttrPageAwareCapacityAllocationPolicy extends CapacityAllocationPolicy {
      boolean myAttrPageRequested;

      @Override
      public int calculateCapacity(int requiredLength) {   // 20% for growth
        return Math.max(myAttrPageRequested ? 8:32, Math.min((int)(requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
      }
    }
  }

  public FSRecords() {
  }

  public static void connect() {
    DbConnection.connect();
  }

  public static long getCreationTimestamp() {
    r.lock();
    try {
      return DbConnection.getTimestamp();
    }
    finally {
      r.unlock();
    }
  }

  private static ResizeableMappedFile getRecords() {
    return DbConnection.myRecords;
  }

  private static PersistentBTreeEnumerator<byte[]> getContentHashesEnumerator() {
    return DbConnection.myContentHashesEnumerator;
  }

  private static RefCountingStorage getContentStorage() {
    return DbConnection.myContents;
  }

  private static Storage getAttributesStorage() {
    return DbConnection.myAttributes;
  }

  public static PersistentStringEnumerator getNames() {
    return DbConnection.getNames();
  }

  // todo: Address  / capacity store in records table, size store with payload
  public static int createRecord() {
    w.lock();
    try {
      DbConnection.markDirty();

      final int free = DbConnection.getFreeRecord();
      if (free == 0) {
        final int fileLength = length();
        LOG.assertTrue(fileLength % RECORD_SIZE == 0);
        int newRecord = fileLength / RECORD_SIZE;
        DbConnection.cleanRecord(newRecord);
        assert fileLength + RECORD_SIZE == length();
        return newRecord;
      }
      else {
        if (lazyVfsDataCleaning) deleteContentAndAttributes(free);
        DbConnection.cleanRecord(free);
        return free;
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static int length() {
    return (int)getRecords().length();
  }
  public static int getMaxId() {
    r.lock();
    try {
      return length()/RECORD_SIZE;
    }
    finally {
      r.unlock();
    }
  }

  static void deleteRecordRecursively(int id) {
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
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void markAsDeletedRecursively(final int id) {
    for (int subrecord : list(id)) {
      markAsDeletedRecursively(subrecord);
    }

    markAsDeleted(id);
  }

  private static void markAsDeleted(final int id) {
    w.lock();
    try {
      DbConnection.markDirty();
      addToFreeRecordsList(id);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void doDeleteRecursively(final int id) {
    for (int subrecord : list(id)) {
      doDeleteRecursively(subrecord);
    }

    deleteRecord(id);
  }

  private static void deleteRecord(final int id) {
    w.lock();
    try {
      DbConnection.markDirty();
      deleteContentAndAttributes(id);

      DbConnection.cleanRecord(id);
      addToFreeRecordsList(id);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void deleteContentAndAttributes(int id) throws IOException {
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
      if (bulkAttrReadSupport) skipRecordHeader(attStream, DbConnection.RESERVED_ATTR_ID, id);

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

  private static void addToFreeRecordsList(int id) {
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    setFlags(id, FREE_RECORD_FLAG, false);
  }

  static int[] listRoots() {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttribute(1, ourChildrenAttr);
        if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;

        try {
          final int count = DataInputOutputUtil.readINT(input);
          int[] result = ArrayUtil.newIntArray(count);
          int prevId = 0;
          for (int i = 0; i < count; i++) {
            DataInputOutputUtil.readINT(input); // Name
            prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId; // Id
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
      throw DbConnection.handleError(e);
    }
  }

  @Override
  public void force() {
    DbConnection.force();
  }

  @Override
  public boolean isDirty() {
    return DbConnection.isDirty();
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

  public static int findRootRecord(@NotNull String rootUrl) {
    w.lock();

    try {
      DbConnection.markDirty();
      final int root = getNames().enumerate(rootUrl);

      final DataInputStream input = readAttribute(1, ourChildrenAttr);
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

      final DataOutputStream output = writeAttribute(1, ourChildrenAttr);
      int id;
      try {
        id = createRecord();

        int index = Arrays.binarySearch(ids, id);
        ids = ArrayUtil.insert(ids, -index - 1, id);
        names = ArrayUtil.insert(names, -index - 1, root);

        saveNameIdSequenceWithDeltas(names, ids, output);
      }
      finally {
        output.close();
      }

      return id;
    } catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static void deleteRootRecord(int id) {
    w.lock();

    try {
      DbConnection.markDirty();
      final DataInputStream input = readAttribute(1, ourChildrenAttr);
      assert input != null;
      int count;
      int[] names;
      int[] ids;
      try {
        count = DataInputOutputUtil.readINT(input);

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

      final DataOutputStream output = writeAttribute(1, ourChildrenAttr);
      try {
        saveNameIdSequenceWithDeltas(names, ids, output);
      }
      finally {
        output.close();
      }
    } catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int[] list(int id) {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttribute(id, ourChildrenAttr);
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
      throw DbConnection.handleError(e);
    }
  }

  public static class NameId {
    public static final NameId[] EMPTY_ARRAY = new NameId[0];
    public final int id;
    public final CharSequence name;
    public final int nameId;

    public NameId(int id, int nameId, @NotNull CharSequence name) {
      this.id = id;
      this.nameId = nameId;
      this.name = name;
    }

    @Override
    public String toString() {
      return name + " (" + id + ")";
    }
  }

  @NotNull
  public static NameId[] listAll(int parentId) {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttribute(parentId, ourChildrenAttr);
        if (input == null) return NameId.EMPTY_ARRAY;

        int count = DataInputOutputUtil.readINT(input);
        NameId[] result = count == 0 ? NameId.EMPTY_ARRAY : new NameId[count];
        int prevId = parentId;
        for (int i = 0; i < count; i++) {
          int id = DataInputOutputUtil.readINT(input) + prevId;
          prevId = id;
          int nameId = getNameId(id);
          result[i] = new NameId(id, nameId, FileNameCache.getVFileName(nameId));
        }
        input.close();
        return result;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static boolean wereChildrenAccessed(int id) {
    try {
      r.lock();
      try {
        return findAttributePage(id, ourChildrenAttr, false) != 0;
      } finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static void updateList(int id, @NotNull int[] children) {
    w.lock();
    try {
      DbConnection.markDirty();
      final DataOutputStream record = writeAttribute(id, ourChildrenAttr);
      DataInputOutputUtil.writeINT(record, children.length);
      int prevId = id;

      Arrays.sort(children);

      for (int child : children) {
        if (child == id) {
          LOG.error("Cyclic parent child relations");
        }
        else {
          DataInputOutputUtil.writeINT(record, child - prevId);
          prevId = child;
        }
      }
      record.close();
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void incModCount(int id) {
    DbConnection.markDirty();
    ourLocalModificationCount++;
    final int count = getModCount() + 1;
    getRecords().putInt(HEADER_GLOBAL_MOD_COUNT_OFFSET, count);

    int parent = id;
    int depth = 10000;
    while (parent != 0) {
      setModCount(parent, count);
      parent = getParent(parent);
      if (depth -- == 0) {
        LOG.error("Cyclic parent child relation? file: " + getName(id));
        return;
      }
    }
  }

  public static int getLocalModCount() {
    return ourLocalModificationCount; // This is volatile, only modified under Application.runWriteAction() lock.
  }

  public static int getModCount() {
    r.lock();
    try {
      return getRecords().getInt(HEADER_GLOBAL_MOD_COUNT_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  public static int getParent(int id) {
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
      throw DbConnection.handleError(e);
    }
  }

  // returns id, parent(id), parent(parent(id)), ...  (already cached id or rootId)
  @NotNull
  public static TIntArrayList getParents(int id, @NotNull ConcurrentIntObjectMap<?> idCache) {
    TIntArrayList result = new TIntArrayList(10);
    r.lock();
    try {
      int parentId;
      do {
        result.add(id);
        if (idCache.containsKey(id)) {
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
      throw DbConnection.handleError(e);
    }
    finally {
      r.unlock();
    }
    return result;
  }

  public static void setParent(int id, int parent) {
    if (id == parent) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    w.lock();
    try {
      incModCount(id);
      putRecordInt(id, PARENT_OFFSET, parent);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int getNameId(int id) {
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
      throw DbConnection.handleError(e);
    }
  }

  public static int getNameId(String name) {
    try {
      r.lock();
      try {
        return getNames().enumerate(name);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static String getName(int id) {
    return getNameSequence(id).toString();
  }

  public static CharSequence getNameSequence(int id) {
    try {
      r.lock();
      try {
        final int nameId = getRecordInt(id, NAME_OFFSET);
        return nameId != 0 ? FileNameCache.getVFileName(nameId) : "";
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static String getNameByNameId(int nameId) {
    try {
      r.lock();
      try {
        return nameId != 0 ? getNames().valueOf(nameId) : "";
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static void setName(int id, @NotNull String name) {
    w.lock();
    try {
      incModCount(id);
      putRecordInt(id, NAME_OFFSET, getNames().enumerate(name));
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int getFlags(int id) {
    r.lock();
    try {
      return getRecordInt(id, FLAGS_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  public static void setFlags(int id, int flags, final boolean markAsChange) {
    w.lock();
    try {
      if (markAsChange) {
        incModCount(id);
      }
      putRecordInt(id, FLAGS_OFFSET, flags);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static long getLength(int id) {
    r.lock();
    try {
      return getRecords().getLong(getOffset(id, LENGTH_OFFSET));
    }
    finally {
      r.unlock();
    }
  }

  public static void setLength(int id, long len) {
    w.lock();
    try {
      incModCount(id);
      getRecords().putLong(getOffset(id, LENGTH_OFFSET), len);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static long getTimestamp(int id) {
    r.lock();
    try {
      return getRecords().getLong(getOffset(id, TIMESTAMP_OFFSET));
    }
    finally {
      r.unlock();
    }
  }

  public static void setTimestamp(int id, long value) {
    w.lock();
    try {
      incModCount(id);
      getRecords().putLong(getOffset(id, TIMESTAMP_OFFSET), value);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int getModCount(int id) {
    r.lock();
    try {
      return getRecordInt(id, MOD_COUNT_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  private static void setModCount(int id, int value) {
    putRecordInt(id, MOD_COUNT_OFFSET, value);
  }

  private static int getContentRecordId(int fileId) {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  private static void setContentRecordId(int id, int value) {
    putRecordInt(id, CONTENT_OFFSET, value);
  }

  private static int getAttributeRecordId(int id) {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  private static void setAttributeRecordId(int id, int value) {
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  private static int getRecordInt(int id, int offset) {
    return getRecords().getInt(getOffset(id, offset));
  }

  private static void putRecordInt(int id, int offset, int value) {
    getRecords().putInt(getOffset(id, offset), value);
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  @Nullable
  public static DataInputStream readContent(int fileId) {
    try {
      int page;
      r.lock();
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
      throw DbConnection.handleError(e);
    }
  }

  @Nullable
  public static DataInputStream readContentById(int contentId) {
    try {
      return doReadContentById(contentId);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  private static DataInputStream doReadContentById(int contentId) throws IOException {
    DataInputStream stream = getContentStorage().readStream(contentId);
    if (useSnappyForCompression) {
      byte[] bytes = CompressionUtil.readCompressed(stream);
      stream = new DataInputStream(new ByteArrayInputStream(bytes));
    }

    return stream;
  }

  @Nullable
  public static DataInputStream readAttributeWithLock(int fileId, FileAttribute att) {
    try {
      r.lock();
      try {
        DataInputStream stream = readAttribute(fileId, att);
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
      throw DbConnection.handleError(e);
    }
  }

  // should be called under r or w lock
  @Nullable
  private static DataInputStream readAttribute(int fileId, FileAttribute attribute) throws IOException {
    checkFileIsValid(fileId);

    int recordId = getAttributeRecordId(fileId);
    if (recordId == 0) return null;
    int encodedAttrId = DbConnection.getAttributeId(attribute.getId());

    Storage storage = getAttributesStorage();

    DataInputStream attrRefs = storage.readStream(recordId);
    int page = 0;

    try {
      if (bulkAttrReadSupport) skipRecordHeader(attrRefs, DbConnection.RESERVED_ATTR_ID, fileId);

      while (attrRefs.available() > 0) {
        final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
        final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

        if (attIdOnPage != encodedAttrId) {
          if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
            attrRefs.skipBytes(attrAddressOrSize);
          }
        } else {
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
    finally {
      attrRefs.close();
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

  private static int findAttributePage(int fileId, FileAttribute attr, boolean toWrite) throws IOException {
    checkFileIsValid(fileId);

    int recordId = getAttributeRecordId(fileId);
    int encodedAttrId = DbConnection.getAttributeId(attr.getId());
    boolean directoryRecord = false;

    Storage storage = getAttributesStorage();

    if (recordId == 0) {
      if (!toWrite) return 0;

      recordId = storage.createNewRecord();
      setAttributeRecordId(fileId, recordId);
      directoryRecord = true;
    }
    else {
      DataInputStream attrRefs = storage.readStream(recordId);

      try {
        if (bulkAttrReadSupport) skipRecordHeader(attrRefs, DbConnection.RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) {
            if (inlineAttributes) {
              return attrAddressOrSize < MAX_SMALL_ATTR_SIZE ? -recordId : attrAddressOrSize - MAX_SMALL_ATTR_SIZE;
            } else {
              return attrAddressOrSize;
            }
          } else {
            if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              attrRefs.skipBytes(attrAddressOrSize);
            }
          }

        }
      }
      finally {
        attrRefs.close();
      }
    }

    if (toWrite) {
      Storage.AppenderStream appender = storage.appendStream(recordId);
      if (bulkAttrReadSupport) {
        if (directoryRecord) {
          DataInputOutputUtil.writeINT(appender, DbConnection.RESERVED_ATTR_ID);
          DataInputOutputUtil.writeINT(appender, fileId);
        }
      }

      DataInputOutputUtil.writeINT(appender, encodedAttrId);
      int attrAddress = storage.createNewRecord();
      DataInputOutputUtil.writeINT(appender, inlineAttributes ? attrAddress + MAX_SMALL_ATTR_SIZE : attrAddress);
      DbConnection.REASONABLY_SMALL.myAttrPageRequested = true;
      try {
        appender.close();
      } finally {
        DbConnection.REASONABLY_SMALL.myAttrPageRequested = false;
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

  private static void checkFileIsValid(int fileId) {
    assert fileId > 0 : fileId;
    // TODO: This assertion is a bit timey, will remove when bug is caught.
    if (!lazyVfsDataCleaning) {
      assert !BitUtil.isSet(getFlags(fileId), FREE_RECORD_FLAG) : "Accessing attribute of a deleted page: " + fileId + ":" + getName(fileId);
    }
  }

  public static int acquireFileContent(int fileId) {
    w.lock();
    try {
      int record = getContentRecordId(fileId);
      if (record > 0) getContentStorage().acquireRecord(record);
      return record;
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static void releaseContent(int contentId) {
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
      throw DbConnection.handleError(e);
    } finally {
      w.unlock();
    }
  }

  public static int getContentId(int fileId) {
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
      throw DbConnection.handleError(e);
    }
  }

  @NotNull
  public static DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new ContentOutputStream(fileId, readOnly);
  }

  private static final MessageDigest myDigest = ContentHashesUtil.createHashDigest();

  public static void writeContent(int fileId, ByteSequence bytes, boolean readOnly) throws IOException {
    try {
      new ContentOutputStream(fileId, readOnly).writeBytes(bytes);
    } catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static int storeUnlinkedContent(byte[] bytes) {
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
      throw DbConnection.handleError(e);
    } finally {
      w.unlock();
    }
  }

  @NotNull
  public static DataOutputStream writeAttribute(final int fileId, @NotNull FileAttribute att) {
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

  private static class ContentOutputStream extends DataOutputStream {
    protected final int myFileId;
    protected final boolean myFixedSize;

    private ContentOutputStream(final int fileId, boolean readOnly) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myFixedSize = readOnly;
    }

    @Override
    public void close() throws IOException {
      super.close();

      try {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
        writeBytes(new ByteSequence(_out.getInternalBuffer(), 0, _out.size()));
      }
      catch (Throwable e) {
        throw DbConnection.handleError(e);
      }
    }

    public void writeBytes(ByteSequence bytes) throws IOException {
      int page;
      RefCountingStorage contentStorage = getContentStorage();
      final boolean fixedSize;
      w.lock();
      try {
        incModCount(myFileId);

        checkFileIsValid(myFileId);

        if (weHaveContentHashes) {
          page = findOrCreateContentRecord(bytes.getBytes(), bytes.getOffset(), bytes.getLength());

          incModCount(myFileId);
          checkFileIsValid(myFileId);

          setContentRecordId(myFileId, page > 0 ? page : -page);

          if (page > 0) return;
          page = -page;
          fixedSize = true;
        } else {
          page = getContentRecordId(myFileId);
          if (page == 0 || contentStorage.getRefCount(page) > 1) {
            page = contentStorage.acquireNewRecord();
            setContentRecordId(myFileId, page);
          }
          fixedSize = myFixedSize;
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
  }

  private static final boolean DO_HARD_CONSISTENCY_CHECK = false;
  private static final boolean DUMP_STATISTICS = weHaveContentHashes;  // TODO: remove once not needed
  private static long totalContents, totalReuses, time;
  private static int contents, reuses;

  private static int findOrCreateContentRecord(byte[] bytes, int offset, int length) throws IOException {
    assert weHaveContentHashes;
    byte[] digest;

    long started = DUMP_STATISTICS ? System.nanoTime():0;
    myDigest.reset();
    myDigest.update(String.valueOf(length - offset).getBytes(Charset.defaultCharset()));
    myDigest.update("\0".getBytes(Charset.defaultCharset()));
    myDigest.update(bytes, offset, length);
    digest = myDigest.digest();
    long done = DUMP_STATISTICS ? System.nanoTime() - started : 0;
    time += done;

    ++contents;
    totalContents += length;

    if (DUMP_STATISTICS && (contents & 0x3FFF) == 0) {
      LOG.info("Contents:"+contents + " of " + totalContents + ", reuses:"+reuses + " of " + totalReuses + " for " + (time / 1000000));
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

  private static class AttributeOutputStream extends DataOutputStream {
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
            incModCount(myFileId);

            return;
          }
          finally {
            w.unlock();
          }
        } else {
          int page;
          w.lock();
          try {
            incModCount(myFileId);
            page = findAttributePage(myFileId, myAttribute, true);
            if (inlineAttributes && page < 0) {
              rewriteDirectoryRecordWithAttrContent(new BufferExposingByteArrayOutputStream());
              page = findAttributePage(myFileId, myAttribute, true);
            }

            if (bulkAttrReadSupport) {
              BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
              BufferExposingByteArrayOutputStream oldOut = _out;
              out = stream;
              writeRecordHeader(DbConnection.getAttributeId(myAttribute.getId()), myFileId, this);
              write(oldOut.getInternalBuffer(), 0, oldOut.size());
              getAttributesStorage()
                .writeBytes(page, new ByteSequence(stream.getInternalBuffer(), 0, stream.size()), myAttribute.isFixedSize());
            } else {
              getAttributesStorage()
                .writeBytes(page, new ByteSequence(_out.getInternalBuffer(), 0, _out.size()), myAttribute.isFixedSize());
            }
          }
          finally {
            w.unlock();
          }
        }
      }
      catch (Throwable e) {
        throw DbConnection.handleError(e);
      }
    }

    protected void rewriteDirectoryRecordWithAttrContent(BufferExposingByteArrayOutputStream _out) throws IOException {
      int recordId = getAttributeRecordId(myFileId);
      assert inlineAttributes;
      int encodedAttrId = DbConnection.getAttributeId(myAttribute.getId());

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
            assert attId == DbConnection.RESERVED_ATTR_ID;
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
            } else {
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
        if (bulkAttrReadSupport) writeRecordHeader(DbConnection.RESERVED_ATTR_ID, myFileId, directoryStream);
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

  public static void dispose() {
    w.lock();
    try {
      DbConnection.force();
      DbConnection.closeFiles();
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      ourIsDisposed = true;
      w.unlock();
    }
  }

  public static void invalidateCaches() {
    DbConnection.createBrokenMarkerFile(null);
  }

  public static void checkSanity() {
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
          LOG.assertTrue(DbConnection.myFreeRecords.contains(id), "Record, marked free, not in free list: " + id);
        }
        else {
          LOG.assertTrue(!DbConnection.myFreeRecords.contains(id), "Record, not marked free, in free list: " + id);
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

  private static void checkRecordSanity(final int id, final int recordCount, final IntArrayList usedAttributeRecordIds,
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

  private static void checkContentsStorageSanity(int id) {
    int recordId = getContentRecordId(id);
    assert recordId >= 0;
    if (recordId > 0) {
      getContentStorage().checkSanity(recordId);
    }
  }

  private static void checkAttributesStorageSanity(int id, IntArrayList usedAttributeRecordIds, IntArrayList validAttributeIds) {
    int attributeRecordId = getAttributeRecordId(id);

    assert attributeRecordId >= 0;
    if (attributeRecordId > 0) {
      try {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
      catch (IOException ex) {
        throw DbConnection.handleError(ex);
      }
    }
  }

  private static void checkAttributesSanity(final int attributeRecordId, final IntArrayList usedAttributeRecordIds,
                                            final IntArrayList validAttributeIds) throws IOException {
    assert !usedAttributeRecordIds.contains(attributeRecordId);
    usedAttributeRecordIds.add(attributeRecordId);

    final DataInputStream dataInputStream = getAttributesStorage().readStream(attributeRecordId);
    try {
      if (bulkAttrReadSupport) skipRecordHeader(dataInputStream, 0, 0);

      while(dataInputStream.available() > 0) {
        int attId = DataInputOutputUtil.readINT(dataInputStream);

        if (!validAttributeIds.contains(attId)) {
          assert persistentAttributesList || !getNames().valueOf(attId).isEmpty();
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
    finally {
      dataInputStream.close();
    }
  }

  public static RuntimeException handleError(Throwable e) {
    return DbConnection.handleError(e);
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
