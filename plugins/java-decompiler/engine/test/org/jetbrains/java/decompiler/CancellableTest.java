// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class CancellableTest {
  public static final int MIN_CALL_NUMBERS = 5;
  private DecompilerTestFixture fixture;

  @BeforeEach
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    CancellationManager cancellationManager = new CancellationManager() {
      private final AtomicInteger myAtomicInteger = new AtomicInteger(0);

      @Override
      public void checkCanceled() throws CanceledException {
        check();
      }

      @Override
      public void startMethod(String className, String methodName) {

      }

      @Override
      public void finishMethod(String className, String methodName) {

      }


      private void check() {
        if (myAtomicInteger.incrementAndGet() > MIN_CALL_NUMBERS) {
          throw new CanceledException(new IllegalArgumentException());
        }
      }
    };

    fixture.setUp(Map.of(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
                         IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1",
                         IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1",
                         IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1"),
                  cancellationManager);
  }

  @AfterEach
  public void tearDown() throws IOException {
    fixture.tearDown();
    fixture = null;
  }

  @Test
  public void testCancellablePrimitiveNarrowing() {
    doCancellableTest("pkg/TestPrimitiveNarrowing");
  }

  @Test
  public void testCancellableClassFields() { doCancellableTest("pkg/TestClassFields"); }

  @Test
  public void testCancellableInterfaceFields() { doCancellableTest("pkg/TestInterfaceFields"); }

  @Test
  public void testCancellableClassLambda() { doCancellableTest("pkg/TestClassLambda"); }

  private void doCancellableTest(String testFile, String... companionFiles) {
    var decompiler = fixture.getDecompiler();

    var classFile = fixture.getTestDataDir().resolve("classes/" + testFile + ".class");
    assertTrue(Files.isRegularFile(classFile));
    for (var file : SingleClassesTest.collectClasses(classFile)) {
      decompiler.addSource(file.toFile());
    }

    for (String companionFile : companionFiles) {
      var companionClassFile = fixture.getTestDataDir().resolve("classes/" + companionFile + ".class");
      assertTrue(Files.isRegularFile(companionClassFile));
      for (var file : SingleClassesTest.collectClasses(companionClassFile)) {
        decompiler.addSource(file.toFile());
      }
    }
    var e = assertThrows(CancellationManager.CanceledException.class, () -> decompiler.decompileContext());
    assertInstanceOf(IllegalArgumentException.class, e.getCause());
  }
}
