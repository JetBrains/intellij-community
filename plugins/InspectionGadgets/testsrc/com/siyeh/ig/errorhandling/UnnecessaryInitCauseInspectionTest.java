// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspectionTest extends LightInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public\n" +
      "class MissingResourceException extends RuntimeException {" +
      "  public MissingResourceException(String s, String className, String key) {}" +
      "  MissingResourceException(String message, String className, String key, Throwable cause) {}" +
      "}"
    };
  }

  public void testSplitDeclarationAssignment() {
    doMemberTest("void foo() {\n" +
                 "     RuntimeException exception = null;\n" +
                 "     try {\n" +
                 "         new java.io.FileInputStream(\"asdf\");\n" +
                 "     } catch (java.io.FileNotFoundException e) {\n" +
                 "         exception = new RuntimeException();\n" +
                 "         exception./*Unnecessary 'Throwable.initCause()' call*/initCause/**/(e);\n" +
                 "     } catch (RuntimeException e) {\n" +
                 "         exception = e;\n" +
                 "     }\n" +
                 "     throw exception;\n" +
                 "}");
  }

  public void testReassigned() {
    doMemberTest("void foo() {\n" +
                 "    try {\n" +
                 "        new java.io.FileInputStream(\"asdf\");\n" +
                 "    } catch (java.io.FileNotFoundException e) {\n" +
                 "        RuntimeException exception = new RuntimeException();\n" +
                 "        e = null;\n" +
                 "        exception.initCause(e);\n" +
                 "        throw exception;\n" +
                 "    }\n" +
                 "}");
  }

  public void testIncompatibleType() {
    doTest("import java.io.*;" +
           "class X {" +
           "  void m() throws Exception {" +
           "    try {" +
           "    }catch (RuntimeException ex) {" +
           "       YException wrapper = new YException(\"foo\");" +
           "       wrapper.initCause(ex);" +
           "       throw wrapper;" +
           "    }" +
           "  }" +
           "" +
           "  class YException extends Exception {" +
           "    public YException(String msg) { super(msg); }" +
           "    public YException(String msg, IOException cause) { super(msg, cause); }" +
           "  }" +
           "}");
  }

  public void testNotAccessible() {
    doTest("import java.util.*;" +
           "class X {" +
           "  void z() {" +
           "    RuntimeException cause = new RuntimeException();\n" +
           "    MissingResourceException e = new MissingResourceException(\"asdf\", \"asdf\", \"asdf\");\n" +
           "    e.initCause(cause);" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryInitCauseInspection();
  }
}