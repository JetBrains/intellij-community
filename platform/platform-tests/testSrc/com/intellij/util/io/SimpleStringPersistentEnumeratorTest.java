// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.DataEnumerator.NULL_ID;
import static org.junit.Assert.assertThrows;

public class SimpleStringPersistentEnumeratorTest extends StringEnumeratorTestBase<SimpleStringPersistentEnumerator> {

  public SimpleStringPersistentEnumeratorTest() {
    super(/*valuesToTest: */ 1_000);
  }


  @Override
  @Ignore
  public void runningMultiThreaded_valuesListedByForEach_alwaysKnownToTryEnumerate() throws Exception {
    throw new AssumptionViolatedException("SimpleStringEnumerator doesn't satisfy the property (see comments in .forEach)");
  }

  @Override
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    throw new AssumptionViolatedException("Not satisfied now -- need to investigate why");
  }

  //TODO RC: move the test up, to StringEnumeratorTestBase (but: currently not all enumerators satisfy!)
  @Test
  public void ifEnumeratorIsAutoCloseable_itsMethodMustFailAfterCloseCalled() throws Exception {
    //assumeTrue(enumerator instanceof AutoCloseable);

    ((AutoCloseable)enumerator).close();

    assertThrows(".enumerate() must fail since enumerator is already .close()-ed", Throwable.class,
                 () -> enumerator.enumerate("anything")
    );
    assertThrows(".tryEnumerate() must fail since enumerator is already .close()-ed", Throwable.class,
                 () -> enumerator.tryEnumerate("anything")
    );
    assertThrows(".valueOf() must fail since enumerator is already .close()-ed", Throwable.class,
                 () -> enumerator.valueOf(NULL_ID)
    );
  }

  @Override
  protected SimpleStringPersistentEnumerator openEnumeratorImpl(@NotNull Path storagePath) throws IOException {
    return new SimpleStringPersistentEnumerator(storageFile);
  }
}
