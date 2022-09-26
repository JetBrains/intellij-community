// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTest.AttributeRecord;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTest.Attributes;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.INLINE_ATTRIBUTE_SMALLER_THAN;
import static org.jetbrains.jetCheck.Generator.constant;
import static org.junit.Assert.*;

/**
 *
 */
public class AttributesStorageOnTheTopOfBlobStoragePropertyBasedTest {

  private static final int PAGE_SIZE = 1 << 14;
  private static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);

  private static final int ITERATION_COUNT = 100;

  @BeforeClass
  public static void beforeClass() throws Exception {
    IndexDebugProperties.DEBUG = true;
  }

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected AttributesStorageOnTheTopOfBlobStorage createStorage(final Path storagePath) throws Exception {
    final PagedFileStorage pagedStorage = new PagedFileStorage(
      storagePath,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true,
      true
    );
    final StreamlinedBlobStorage storage = new StreamlinedBlobStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(256, 64, 30)
    );
    return new AttributesStorageOnTheTopOfBlobStorage(storage);
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
          try (AttributesStorageOnTheTopOfBlobStorage storage = createStorage(temporaryFolder.newFile().toPath())) {
            final List<AttributeRecord> records = new ArrayList<>();
            //updates 10x against insert/delete
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

  public static class InsertAttribute implements ImperativeCommand {
    private final Attributes attributes;
    private final AttributesStorageOnTheTopOfBlobStorage storage;
    private final List<AttributeRecord> records;

    public InsertAttribute(@NotNull final Attributes attributes,
                           @NotNull final AttributesStorageOnTheTopOfBlobStorage storage,
                           @NotNull final List<AttributeRecord> records) {
      this.attributes = attributes;
      this.storage = storage;
      this.records = records;
    }

    @Override
    public void performCommand(@NotNull final ImperativeCommand.Environment env) {
      try {
        //FIXME RC: this way we can create >1 AttributeRecords with same fileId/attributeId,
        // which leads to property failure (i.e. one of the record !exist because another one
        // was deleted)
        final int fileId = env.generateValue(Generator.integers(0, Integer.MAX_VALUE),
                                             "Generated fileId: %s");
        final int attributeId = env.generateValue(Generator.integers(0, 1024),
                                                  "Generated attributeId: %s");

        final AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(
          AttributeRecord.newAttributeRecord(fileId, attributeId)
            .withRandomAttributeBytes(1029),
          storage
        );
        records.add(insertedRecord);

        assertTrue(
          insertedRecord.existsInStorage(storage)
        );
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static class UpdateAttribute implements ImperativeCommand {
    private final Attributes attributes;
    private final AttributesStorageOnTheTopOfBlobStorage storage;
    private final List<AttributeRecord> records;

    public UpdateAttribute(@NotNull final Attributes attributes,
                           @NotNull final AttributesStorageOnTheTopOfBlobStorage storage,
                           @NotNull final List<AttributeRecord> records) {
      this.attributes = attributes;
      this.storage = storage;
      this.records = records;
    }

    @Override
    public void performCommand(@NotNull final ImperativeCommand.Environment env) {
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
        }
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static class DeleteAttribute implements ImperativeCommand {
    private final Attributes attributes;
    private final AttributesStorageOnTheTopOfBlobStorage storage;
    private final List<AttributeRecord> records;

    public DeleteAttribute(@NotNull final Attributes attributes,
                           @NotNull final AttributesStorageOnTheTopOfBlobStorage storage,
                           @NotNull final List<AttributeRecord> records) {
      this.attributes = attributes;
      this.storage = storage;
      this.records = records;
    }

    @Override
    public void performCommand(@NotNull final ImperativeCommand.Environment env) {
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


  /* ======================== infrastructure ============================================================== */
}