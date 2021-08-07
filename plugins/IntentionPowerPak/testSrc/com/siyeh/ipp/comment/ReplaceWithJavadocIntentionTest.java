// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.comment;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import org.jetbrains.annotations.NonNls;

public class ReplaceWithJavadocIntentionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected @NonNls String getBasePath() {
    return "/comment/to_javadoc";
  }
}