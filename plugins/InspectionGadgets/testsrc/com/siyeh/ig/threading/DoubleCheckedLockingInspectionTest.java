// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DoubleCheckedLockingInspectionTest extends LightJavaInspectionTestCase {

  public void testDoubleCheckedLocking() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testQuickFix() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  public void testQuickFixNotAvailable() {
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @SuppressWarnings("DoubleCheckedLocking")
  public void testSimple() {
    doTest("class A {" +
           "    private  boolean initialized;\n" +
           "    private void initialize() {\n" +
           "        /*Double-checked locking*/if/**/ (initialized == false) {\n" +
           "            synchronized (this) {\n" +
           "                if (initialized == false) {\n" +
           "                    initialized = true;\n" +
           "                }\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testVolatile() {
    doTest("class X {" +
           "    private volatile boolean initialized;\n" +
           "    private void initialize() {\n" +
           "        if (initialized == false) {\n" +
           "            synchronized (this) {\n" +
           "                if (initialized == false) {\n" +
           "                    initialized = true;\n" +
           "                }\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testVolatile2() {
    doTest("class Main654 {\n" +
           "  private volatile int myListenPort = -1;\n" +
           "  private void ensureListening() {\n" +
           "    if (myListenPort < 0) {\n" +
           "      synchronized (this) {\n" +
           "        if (myListenPort < 0) {\n" +
           "          myListenPort = startListening();\n" +
           "        }\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "  private int startListening() {\n" +
           "    return 0;\n" +
           "  }\n" +
           "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new DoubleCheckedLockingInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}
