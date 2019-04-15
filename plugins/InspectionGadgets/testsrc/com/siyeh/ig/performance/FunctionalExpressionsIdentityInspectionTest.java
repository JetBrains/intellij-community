// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class FunctionalExpressionsIdentityInspectionTest extends LightInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new FunctionalExpressionsIdentityInspection();
  }

  public void testFunctionalExpressionsIdentity() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package java.util;" +
      "public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {" +
      "  public boolean add(E e) { return false; }" +
      "}",

      "package com.siyeh.igtest.performance.functional_expressions_identity;" +
      "public interface MyFuncInterface {" +
      "void functionalMethod();}"
    };
  }
}
