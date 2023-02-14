// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import junit.framework.Test;

public final class AllTests {
  public static Test suite() throws Throwable {
    return new TestAll("");
  }
}
