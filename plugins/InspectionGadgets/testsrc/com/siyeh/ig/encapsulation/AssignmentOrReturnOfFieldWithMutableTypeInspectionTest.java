// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentOrReturnOfFieldWithMutableTypeInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package com.google.common.collect;

import java.util.List;

public class ImmutableList<E> implements List<E> {
  public static ImmutableList<?> of() {return new ImmutableList<>();}
  public static <T> ImmutableList<T> copyOf(List<T> list) {return new ImmutableList<>();}
}"""
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11_ANNOTATED;
  }

  public void testAssignmentOrReturnOfFieldWithMutableType() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AssignmentOrReturnOfFieldWithMutableTypeInspection();
  }
}