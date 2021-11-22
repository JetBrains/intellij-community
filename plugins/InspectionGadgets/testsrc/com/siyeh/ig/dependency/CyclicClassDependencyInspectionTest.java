// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dependency;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class CyclicClassDependencyInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/dependency/cyclic_class_dependency", new CyclicClassDependencyInspection());
  }

  public void testOption() {
    final CyclicClassDependencyInspection inspection = new CyclicClassDependencyInspection();
    inspection.ignoreInSameFile = true;
    doTest("com/siyeh/igtest/dependency/cyclic_class_dependency2", inspection);
  }
}
