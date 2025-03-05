// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl.heavy.attributes;

import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase.AttributeRecord;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase.Attributes;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.VFSAttributesStorage;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageHelper;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverLockFreePagedStorage;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverPagedStorage;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.VFSAttributesStorage.INLINE_ATTRIBUTE_SMALLER_THAN;
import static org.jetbrains.jetCheck.Generator.constant;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AttributesStorageOnTheTopOfBlobStorage_PropertyBasedTest {

  private static final int PAGE_SIZE = 1 << 14;
  private static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);

  private static final SpaceAllocationStrategy SPACE_ALLOCATION_STRATEGY = new DataLengthPlusFixedPercentStrategy(
    64, 256,
    StreamlinedBlobStorageHelper.MAX_CAPACITY,
    30
  );

  private static final int ITERATION_COUNT = 100;

  @BeforeClass
  public static void beforeClass() {
    IndexDebugProperties.DEBUG = true;
  }

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();


  @Parameterized.Parameters
  public static List<Object[]> storageFactoriesToTest() {
    List<Object[]> storages = new ArrayList<>();

    storages.add(new Object[]{
      (StorageFactory<StreamlinedBlobStorageOverPagedStorage>)storagePath -> {
        return new StreamlinedBlobStorageOverPagedStorage(
          new PagedFileStorage(storagePath, LOCK_CONTEXT, PAGE_SIZE, true, true),
          SPACE_ALLOCATION_STRATEGY
        );
      }
    });
    storages.add(new Object[]{
      MMappedFileStorageFactory.withDefaults()
        .pageSize(PAGE_SIZE)
        .compose(storage -> new StreamlinedBlobStorageOverMMappedFile(storage, SPACE_ALLOCATION_STRATEGY))
    });

    if (PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED) {
      storages.add(new Object[]{
        (StorageFactory<StreamlinedBlobStorageOverLockFreePagedStorage>)storagePath -> {
          return new StreamlinedBlobStorageOverLockFreePagedStorage(
            new PagedFileStorageWithRWLockedPageContent(
              storagePath, LOCK_CONTEXT, PAGE_SIZE, PageContentLockingStrategy.LOCK_PER_PAGE
            ),
            SPACE_ALLOCATION_STRATEGY
          );
        }
      });
    }


    return storages;
  }

  private final StorageFactory<? extends StreamlinedBlobStorage> storageFactory;

  public AttributesStorageOnTheTopOfBlobStorage_PropertyBasedTest(@NotNull StorageFactory<? extends StreamlinedBlobStorage> storageFactory) {
    this.storageFactory = storageFactory;
  }

  protected AttributesStorageOverBlobStorage createStorage(Path storagePath) throws Exception {
    StreamlinedBlobStorage storage = storageFactory.open(storagePath);
    return new AttributesStorageOverBlobStorage(storage);
  }

  @Test
  public void insertsUpdatesDeletesAttributesInAnyOrderCreateCoherentStorageBehaviour() {
    PropertyChecker.customized()
      .withIterationCount(ITERATION_COUNT)
      .withSizeHint(iterNo -> 10 * iterNo)
      //.printRawData()
      //.printGeneratedValues()
      .checkScenarios(() -> {
        return env -> {
          final Attributes attributes = new Attributes();
          try (AttributesStorageOverBlobStorage storage = createStorage(temporaryFolder.newFile().toPath())) {
            final List<AttributeRecord> records = new ArrayList<>();
            //updates count 10x of inserts/deletes
            final Generator<ImperativeCommand> commandsGenerator = Generator.frequency(
              1, constant(new InsertAttribute(attributes, storage, records)),
              10, constant(new UpdateAttribute(attributes, storage, records)),
              1, constant(new DeleteAttribute(attributes, storage, records))
            );
            env.executeCommands(commandsGenerator);
            env.logMessage("Total records: " + records.size());
          }
          catch (Exception e) {
            throw new AssertionError(e);
          }
        };
      });
  }

  /* ======================== infrastructure ============================================================== */

  public static class InsertAttribute implements ImperativeCommand {
    private final Attributes attributes;
    private final AttributesStorageOverBlobStorage storage;
    private final List<AttributeRecord> records;

    private final Int2IntMap fileIdToAttributeId = new Int2IntOpenHashMap();

    public InsertAttribute(final @NotNull Attributes attributes,
                           final @NotNull AttributesStorageOverBlobStorage storage,
                           final @NotNull List<AttributeRecord> records) {
      this.attributes = attributes;
      this.storage = storage;
      this.records = records;
    }

    @Override
    public void performCommand(final @NotNull ImperativeCommand.Environment env) {
      try {
        while (true) {
          final int fileId = env.generateValue(Generator.integers(0, Integer.MAX_VALUE),
                                               "Generated fileId: %s");
          final int attributeId = env.generateValue(Generator.integers(0, VFSAttributesStorage.MAX_ATTRIBUTE_ID),
                                                    "Generated attributeId: %s");
          //RC: we can create >1 AttributeRecords with the same fileId/attributeId, which leads to
          // property failure (i.e. one of the record found not exist because another one was
          // deleted) -> prevents it:
          if (fileIdToAttributeId.get(fileId) == attributeId) {
            continue;
          }
          fileIdToAttributeId.put(fileId, attributeId);

          final AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(
            AttributeRecord.newAttributeRecord(fileId, attributeId)
              .withRandomAttributeBytes(1029),
            storage
          );
          records.add(insertedRecord);
          assertTrue(
            insertedRecord.existsInStorage(storage)
          );
          return;
        }
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static class UpdateAttribute implements ImperativeCommand {
    private final Attributes attributes;
    private final AttributesStorageOverBlobStorage storage;
    private final List<AttributeRecord> records;

    public UpdateAttribute(final @NotNull Attributes attributes,
                           final @NotNull AttributesStorageOverBlobStorage storage,
                           final @NotNull List<AttributeRecord> records) {
      this.attributes = attributes;
      this.storage = storage;
      this.records = records;
    }

    @Override
    public void performCommand(final @NotNull ImperativeCommand.Environment env) {
      try {
        if (!records.isEmpty()) {
          final int recordIndex = env.generateValue(Generator.integers(0, records.size() - 1),
                                                    "Attribute to torture: #%s");
          final AttributeRecord record = records.get(recordIndex);

          final int attributeSize = record.attributeBytesLength();
          //grow small attributes, shrink big attributes:
          final Generator<Integer> sizeGenerator;
          if (attributeSize <= INLINE_ATTRIBUTE_SMALLER_THAN) {
            sizeGenerator = Generator.integers(attributeSize, 4 * INLINE_ATTRIBUTE_SMALLER_THAN);
          }
          else {
            sizeGenerator = Generator.integers(0, 2 * INLINE_ATTRIBUTE_SMALLER_THAN);
          }
          final Integer newSize = env.generateValue(sizeGenerator,
                                                    "New attribute size: %s bytes");

          final AttributeRecord updatedRecord = attributes.insertOrUpdateRecord(
            record.withRandomAttributeBytes(newSize),
            storage
          );

          records.set(recordIndex, updatedRecord);

          assertTrue(
            updatedRecord.existsInStorage(storage)
          );
          assertArrayEquals(
            updatedRecord.readValueFromStorage(storage),
            updatedRecord.attributeBytes()
          );
          assertArrayEquals(
            updatedRecord.readValueFromStorageRaw(storage),
            updatedRecord.attributeBytes()
          );
        }
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static class DeleteAttribute implements ImperativeCommand {
    private final Attributes attributes;
    private final AttributesStorageOverBlobStorage storage;
    private final List<AttributeRecord> records;

    public DeleteAttribute(final @NotNull Attributes attributes,
                           final @NotNull AttributesStorageOverBlobStorage storage,
                           final @NotNull List<AttributeRecord> records) {
      this.attributes = attributes;
      this.storage = storage;
      this.records = records;
    }

    @Override
    public void performCommand(final @NotNull ImperativeCommand.Environment env) {
      try {
        if (!records.isEmpty()) {
          final int recordIndex = env.generateValue(Generator.integers(0, records.size() - 1),
                                                    "Attribute to delete: #%s");

          final AttributeRecord recordToDelete = records.remove(recordIndex);
          attributes.deleteRecord(recordToDelete, storage);

          assertFalse(
            recordToDelete.existsInStorage(storage)
          );
        }
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}