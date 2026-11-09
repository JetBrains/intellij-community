// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.vfs.CheckedDiskQueryRelay;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/// The test is in Java because it checks the compiler: a Java caller must be able to catch the checked exception of the function.
/// [DiskQueryRelayTest] checks the same exceptions at run time, in a cancellable and in a non-cancellable context.
class CheckedDiskQueryRelayTest {
  @Test
  void javaCallerCatchesTheCheckedException_ofTheRelayFunction() {
    IOException failure = new IOException("checked");
    CheckedDiskQueryRelay<Integer, String, IOException> relay = CheckedDiskQueryRelay.create(arg -> {
      throw failure;
    });
    // `catch (IOException)` compiles only if the relay declares `throws IOException`
    try {
      relay.accessDiskWithCheckCanceled(1);
      fail("The relay must throw the exception of the function");
    }
    catch (IOException e) {
      assertSame(failure, e, "The relay must throw the exception of the function as is, not wrapped");
    }
  }

  @Test
  void javaCallerCatchesTheCheckedException_ofTheComputeTask() {
    IOException failure = new IOException("checked");
    // `catch (IOException)` compiles only if compute() declares `throws IOException`
    try {
      CheckedDiskQueryRelay.<String, IOException>compute(() -> {
        throw failure;
      });
      fail("compute() must throw the exception of the task");
    }
    catch (IOException e) {
      assertSame(failure, e, "compute() must throw the exception of the task as is, not wrapped");
    }
  }
}
