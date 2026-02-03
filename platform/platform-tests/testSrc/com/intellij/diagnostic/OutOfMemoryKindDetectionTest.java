// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.junit.Test;

import static com.intellij.diagnostic.VMOptions.MemoryKind.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OutOfMemoryKindDetectionTest {
  @Test
  public void test() {
    assertEquals(HEAP, DefaultIdeaErrorLogger.getOOMErrorKind(new OutOfMemoryError()));
    assertEquals(HEAP, DefaultIdeaErrorLogger.getOOMErrorKind(new OutOfMemoryError("whatever")));

    assertNull(DefaultIdeaErrorLogger.getOOMErrorKind(new OutOfMemoryError("unable to create new native thread")));
    assertNull(DefaultIdeaErrorLogger.getOOMErrorKind(new OutOfMemoryError("unable to create native thread: ... limits reached")));

    assertEquals(METASPACE, DefaultIdeaErrorLogger.getOOMErrorKind(new OutOfMemoryError("Metaspace")));

    assertEquals(DIRECT_BUFFERS, DefaultIdeaErrorLogger.getOOMErrorKind(new OutOfMemoryError("Cannot reserve X bytes of direct buffer memory ...")));

    assertEquals(CODE_CACHE, DefaultIdeaErrorLogger.getOOMErrorKind(new InternalError("CodeCache is full")));
  }
}
