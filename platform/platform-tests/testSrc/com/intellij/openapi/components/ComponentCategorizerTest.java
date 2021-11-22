// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.testFramework.LightPlatformTestCase;

public class ComponentCategorizerTest extends LightPlatformTestCase {
  public void testGetCategoryFromAnnotation() {
    PersistentStateComponent component = ApplicationManager.getApplication().getService(AppEditorFontOptions.class);
    SettingsCategory category = ComponentCategorizer.getCategory(component);
    assertEquals(SettingsCategory.UI, category);
  }
}
