// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.trivialif;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

public class MergeIfOrIntentionTest extends LightIntentionActionTestCase {

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/mergeIfOr";
  }
}
