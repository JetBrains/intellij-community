// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.logging;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.logging.RedundantObjectArrayCreationInLoggingInspection;

public class RedundantObjectArrayCreationInLoggingInspectionTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultHint = String.format("Fix all '%s' problems in file",
                                  InspectionGadgetsBundle.message("redundant.object.array.creation.in.logging"));
    myFixture.enableInspections(new RedundantObjectArrayCreationInLoggingInspection());
  }

  public void testObjectArrayCreationInLoggingDebug() { doTest(); }
  public void testObjectArrayCreationInLoggingInfo() { doTest(); }
  public void testObjectArrayCreationInLoggingWarn() { doTest(); }
  public void testObjectArrayCreationInLoggingError() { doTest(); }
  public void testObjectArrayCreationInLoggingTrace() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "logging/redundant_object_array_creation_in_logging";
  }
}
