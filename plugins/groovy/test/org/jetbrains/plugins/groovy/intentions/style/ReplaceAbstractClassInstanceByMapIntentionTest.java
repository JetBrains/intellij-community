// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceAbstractClassInstanceByMapIntentionTest extends GrIntentionTestCase {

  public ReplaceAbstractClassInstanceByMapIntentionTest() {
    super("Change to dynamic instantiation");
  }

  public void testUnresolved() { doTest(true); }

  @Override
  public String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/changeToDynamicInstantiation/";
  }
}
