// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usageView;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.util.ui.UIUtil;

@Service
public final class UsageTreeColorsScheme {
  public static UsageTreeColorsScheme getInstance() {
    return ServiceManager.getService(UsageTreeColorsScheme.class);
  }

  public EditorColorsScheme getScheme() {
    return EditorColorsUtil.getColorSchemeForBackground(UIUtil.getTreeBackground());
  }
}
