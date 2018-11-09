// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.forloop.imports;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.IntentionPowerPackBundle;

/**
 * @author peter
 */
public class ReplaceOnDemandImportTest extends LightCodeInsightFixtureTestCase {

  public void testUnavailableOnBrokenCode() {
    myFixture.configureByText("a.java", "import java.uti<caret>l.;");
    assertEmpty(myFixture.filterAvailableIntentions(IntentionPowerPackBundle.message("replace.on.demand.import.intention.name")));
  }
  
}
