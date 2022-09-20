// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.fail;

/**
 * Basically, the testcase is about reading values from yet unallocated addresses of {@link ResizeableMappedFile}.
 * Currently {@link ResizeableMappedFile} allows it, but this is a kind of undocumented feature, which wasn't really
 * designed that way, but just so happens -- and client code rely on it sometimes.
 *
 * If underlying buffer of {@link ResizeableMappedFile} wasn't zeroed before use it could contain anything (most
 * likely values from previous use) and reads from unallocated area will return that "anything". This is dangerous
 * by itself, but it could lead to even more interesting issues in scenarios with conditional writes, like
 * if(newValue != getLong(addr)) -> putLong(addr, newValue). If one uses conditional write, and it so happens
 * garbage from unallocated area == newValue, then write will be bypassed. But next not-bypassed write will
 * 'allocate' the area, and _zero it_. Now buffer contains zeros there previously it has contained newValue,
 * and next try to re-read getLong(addr) will return 0, while it is expected to return newValue -- because
 * we just (conditionally) put it there!
 *
 * Now this may look as a very rare case, because it is unlikely to have arbitrary int/long value to be equal to your
 * newValue. But it is not as rare as one may think, because values in unallocated areas of buffer is not something
 * random -- they are the values from previous use. So if app has a set of values it frequently writes into RMF (e.g. flags,
 * or booleans, or just a lot of small numbers), then chance of such a 'collision' is not minor.
 */
public class ResizeableMappedFileReadFromUnAllocatedAreaTest {
  public static final int SIZE = 1 << 16;

  @ClassRule
  public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  private ResizeableMappedFile mappedFile;

  @BeforeClass
  public static void beforeClass() throws Exception {
    //Important piece of preparation: create RMF, fill it with Long.MAX_VALUE, and close it.
    // This dirties internal buffers with Long.MAX_VALUE, and the same buffers will be reused
    // lately in an actual test.
    try (ResizeableMappedFile mappedFile = createResizeableMappedFile(TEMPORARY_FOLDER.newFile())) {
      for (int i = 0; i < SIZE; i++) {
        final int offset = i * Long.BYTES;
        mappedFile.putLong(offset, Long.MAX_VALUE);
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    mappedFile = createResizeableMappedFile(TEMPORARY_FOLDER.newFile());
  }

  @After
  public void tearDown() throws Exception {
    mappedFile.close();
  }

  @Test
  public void values_Conditionally_WrittenCouldBeReadBackUnchanged() throws IOException {
    final long[] swarmOfLongs = generateLongArray(SIZE);

    //write all value _conditionally_: check is it not written yet
    for (int i = 0; i < swarmOfLongs.length; i++) {
      final long newValue = swarmOfLongs[i];
      final int offset = i * Long.BYTES;
      final long currentValue = mappedFile.getLong(offset);
      if (currentValue != newValue) {
        mappedFile.putLong(offset, newValue);
      }
    }

    //now check again we have indeed written all that longs:
    final StringBuilder errors = new StringBuilder();
    for (int i = 0; i < swarmOfLongs.length; i++) {
      final long valueWritten = swarmOfLongs[i];
      final int offset = i * Long.BYTES;
      final long valueRead = mappedFile.getLong(offset);
      if (valueRead != valueWritten) {
        errors.append("\twritten[index: " + i + "] = " + valueWritten + ", read[offset: " + offset + "] = " + valueRead + "\n");
      }
    }

    if (!errors.isEmpty()) {
      fail("All values written are expected to be read back, but there are some that fail: \n"
           + errors);
    }
  }

  private static ResizeableMappedFile createResizeableMappedFile(final File storageFile) throws IOException {
    final int pageSize = PagedFileStorage.DEFAULT_PAGE_SIZE;
    final StorageLockContext storageLockContext = new StorageLockContext(true, true, true);
    return new ResizeableMappedFile(
      storageFile.toPath(),
      1024,
      storageLockContext,
      pageSize,
      true,
      IOUtil.useNativeByteOrderForByteBuffers()
    );
  }

  private static long[] generateLongArray(final int size) {
    final long[] swarmOfLongs = new long[size];
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < swarmOfLongs.length; i++) {
      swarmOfLongs[i] = rnd.nextBoolean() ? rnd.nextLong() : Long.MAX_VALUE;
    }
    return swarmOfLongs;
  }
}
