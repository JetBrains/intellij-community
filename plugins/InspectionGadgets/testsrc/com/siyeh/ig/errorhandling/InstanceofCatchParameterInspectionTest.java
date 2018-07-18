// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class InstanceofCatchParameterInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doStatementTest("try {" +
                    "} catch (Exception e) {" +
                    "  if ((/*'instanceof' on 'catch' parameter 'e'*/e/**/) instanceof NullPointerException) {}" +
                    "}");
  }

  public void testNoWarn() {
    doStatementTest("try {" +
                    "} catch (RuntimeException e) {" +
                    "  if (e instanceof ControlFlowException) {" +
                    "    return;" +
                    "  }" +
                    "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InstanceofCatchParameterInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    //noinspection NonExceptionNameEndsWithException
    return new String[] {
      "public interface ControlFlowException {}"
    };
  }
}