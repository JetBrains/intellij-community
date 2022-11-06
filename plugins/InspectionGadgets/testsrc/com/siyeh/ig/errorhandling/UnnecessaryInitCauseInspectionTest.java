// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspectionTest extends LightJavaInspectionTestCase {

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
    doMemberTest("""
                   void foo() {
                        RuntimeException exception = null;
                        try {
                            new java.io.FileInputStream("asdf");
                        } catch (java.io.FileNotFoundException e) {
                            exception = new RuntimeException();
                            exception./*Unnecessary 'Throwable.initCause()' call*/initCause/**/(e);
                        } catch (RuntimeException e) {
                            exception = e;
                        }
                        throw exception;
                   }""");
  }

  public void testReassigned() {
    doMemberTest("""
                   void foo() {
                       try {
                           new java.io.FileInputStream("asdf");
                       } catch (java.io.FileNotFoundException e) {
                           RuntimeException exception = new RuntimeException();
                           e = null;
                           exception.initCause(e);
                           throw exception;
                       }
                   }""");
  }

  public void testIncompatibleType() {
    //noinspection EmptyTryBlock
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

  @SuppressWarnings("ThrowableNotThrown")
  public void testNotAccessible() {
    doTest("""
             import java.util.*;class X {  static void z() {    RuntimeException cause = new RuntimeException();
                 MissingResourceException e = new MissingResourceException("asdf", "asdf", "asdf");
                 e.initCause(cause);  }}""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryInitCauseInspection();
  }
}