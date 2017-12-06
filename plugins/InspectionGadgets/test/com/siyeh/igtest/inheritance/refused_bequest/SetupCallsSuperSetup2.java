/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
import junit.framework.TestCase;

class SetupCallsSuperSetup2 extends TestCase {

  protected void setUp() throws Exception {
    System.out.println("foo");
  }
}
