// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public final class DataManagerTest extends LightPlatformCodeInsightTestCase {
  public void testExplicitNull() {
    DataContext context1 = SimpleDataContext.builder()
      .add(CommonDataKeys.LANGUAGE, Language.ANY).build();
    DataContext context2 = SimpleDataContext.builder()
      .addNull(CommonDataKeys.LANGUAGE).setParent(context1).build();
    assertEquals(Language.ANY, context1.getData(CommonDataKeys.LANGUAGE));
    assertNull(context2.getData(CommonDataKeys.LANGUAGE));
    DataContext async2 = Utils.createAsyncDataContext(context2);
    assertNull(async2.getData(CommonDataKeys.LANGUAGE));
  }
}
