/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
