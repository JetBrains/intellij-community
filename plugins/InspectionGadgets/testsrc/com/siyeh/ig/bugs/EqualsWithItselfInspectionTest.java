// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class EqualsWithItselfInspectionTest extends LightJavaInspectionTestCase {

  public void testEqualsWithItself() {
    doTest();
  }

  public void testEqualsWithItself_ignoreNonFinalClasses() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    EqualsWithItselfInspection inspection = new EqualsWithItselfInspection();
    String option = StringUtil.substringAfter(getName(), "_");
    if(option != null) {
      new OptionAccessor.Default(inspection).setOption(option, true);
    }
    return inspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {"""
     package org.junit.jupiter.api;
     public class Assertions{
     	public static void assertEquals(Object expected, Object actual) {}
     	public static void assertNotEquals(Object expected, Object actual) {}
     }
     """,
     """
     package org.junit;
     public class Assert{
     	public static void assertSame(Object expected, Object actual) {}
     }
     """,
     """
     package org.assertj.core.api;
     import org.assertj.core.api.AbstractAssert;
     public class Assertions{
      public static AbstractAssert assertThat(Object actual) {
        return new AbstractAssert();
      }
     }
     """,
     """
     package org.assertj.core.api;
     public class AbstractAssert{
      public AbstractAssert isEqualTo(Object expected) {
       return this;
      }
      public AbstractAssert anotherTest(Object expected) {
       return this;
      }
     }
     """,
    };
  }
}