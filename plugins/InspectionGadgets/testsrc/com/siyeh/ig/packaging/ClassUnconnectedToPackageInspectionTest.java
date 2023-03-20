// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.packaging;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ClassUnconnectedToPackageInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/packaging/class_unconnected_to_package", new ClassUnconnectedToPackageInspection());
  }
}
