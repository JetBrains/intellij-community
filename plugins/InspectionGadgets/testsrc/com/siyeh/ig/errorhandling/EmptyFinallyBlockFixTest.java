// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class EmptyFinallyBlockFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new EmptyFinallyBlockInspection();
  }

  public void testRemoveTry() {
    doMemberTest(InspectionGadgetsBundle.message("remove.try.finally.block.quickfix"),
                 "void m() throws Exception {\n" +
                 "  try { throw new Exception(); }\n" +
                 "  finally/**/ { }\n" +
                 "}",
                 "void m() throws Exception {\n" +
                 "    throw new Exception();\n" +
                 "}"
    );
  }

  public void testWithResource() {
    doMemberTest(InspectionGadgetsBundle.message("remove.finally.block.quickfix"),
                 "void m() throws Exception {\n" +
                 "  try (AutoCloseable r = null) { throw new Exception(); }\n" +
                 "  finally/**/ { }\n" +
                 "}",
                 "void m() throws Exception {\n" +
                 "  try (AutoCloseable r = null) { throw new Exception(); }\n" +
                 "}"
    );
  }

  public void testWithCatch() {
    doMemberTest(InspectionGadgetsBundle.message("remove.finally.block.quickfix"),
                 "void m() throws Exception {\n" +
                 "  try { throw new Exception(); }\n" +
                 "  catch (Exception e) { e.printStackTrace(); }\n" +
                 "  finally/**/ { }\n" +
                 "}",
                 "void m() throws Exception {\n" +
                 "  try { throw new Exception(); }\n" +
                 "  catch (Exception e) { e.printStackTrace(); }\n" +
                 "}"
    );
  }

  public void testDoNotCleanupWithFilledFinally() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.finally.block.quickfix"),
                               "class X {\n" +
                               "void m() throws Exception {\n" +
                               "  try { throw new Exception(); }\n" +
                               "  finally/**/ { System.out.println(\"foo\"); }\n" +
                               "}" +
                               "}\n");
  }
}
