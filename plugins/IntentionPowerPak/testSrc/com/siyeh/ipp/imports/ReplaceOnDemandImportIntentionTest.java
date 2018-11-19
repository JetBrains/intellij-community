// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.imports;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ReplaceOnDemandImportIntentionTest extends IPPTestCase {

  public void testStaticImport() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.on.demand.import.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "imports";
  }
}
