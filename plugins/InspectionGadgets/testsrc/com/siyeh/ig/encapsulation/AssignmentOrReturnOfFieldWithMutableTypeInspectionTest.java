// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentOrReturnOfFieldWithMutableTypeInspectionTest extends LightInspectionTestCase {
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk18());
    }
  };

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package com.google.common.collect;\n" +
      "\n" +
      "import java.util.List;\n" +
      "\n" +
      "public class ImmutableList<E> implements List<E> {\n" +
      "  public static ImmutableList<?> of() {return new ImmutableList<>();}\n" +
      "  public static <T> ImmutableList<T> copyOf(List<T> list) {return new ImmutableList<>();}\n" +
      "}"
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
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