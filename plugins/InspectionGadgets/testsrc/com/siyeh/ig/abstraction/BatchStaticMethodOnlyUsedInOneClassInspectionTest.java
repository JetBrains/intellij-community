/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.abstraction;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class BatchStaticMethodOnlyUsedInOneClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/abstraction/static_method_only_used_in_one_class/global", new StaticMethodOnlyUsedInOneClassInspection());
  }
}
