// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.asserttoif;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.asserttoif.IfCanBeAssertionInspection;

/**
 * @author Bas Leijdekkers
 */
public class IfCanBeAssertionInspectionTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new IfCanBeAssertionInspection());
    myFixture.addClass("package java.util;\n" +
                       "public class Objects {\n" +
                       "    public static <T> T requireNonNull(T obj) {\n" +
                       "        if (obj == null)\n" +
                       "            throw new NullPointerException();\n" +
                       "        return obj;\n" +
                       "    }\n" +
                       "    public static <T> T requireNonNull(T obj, String msg) {\n" +
                       "        if (obj == null)\n" +
                       "            throw new NullPointerException();\n" +
                       "        return obj;\n" +
                       "    }\n" +
                       "}");
    myFixture.addClass("package com.google.common.base;\n" +
                       "public class Preconditions {\n" +
                       "    public static <T> T checkNotNull(T obj) {\n" +
                       "        if (obj == null)\n" +
                       "            throw new NullPointerException();\n" +
                       "        return obj;\n" +
                       "    }\n" +
                       "    public static <T> T checkNotNull(T obj, Object msg) {\n" +
                       "        if (obj == null)\n" +
                       "            throw new NullPointerException();\n" +
                       "        return obj;\n" +
                       "    }\n" +
                       "}");
    myRelativePath = "asserttoif/if_to_assert";
    myDefaultHint = InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.assertion.quickfix");
  }

  public void testRandomThrowable() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testParenthesesRequireNonNull() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testPreconditions1() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testPreconditions2() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testPreconditions3() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testObjectsRequireNonNull() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testObjectsRequireNonNullNoMessage() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testNoCondition() { assertQuickfixNotAvailable(); }
  public void testInequalityCondition() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
}
