// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// todo: remove and use DarkIconProvider when JBSDK supports scalable icons in menu
public interface MenuBarIconProvider {
  @NotNull
  Icon getMenuBarIcon(boolean isDark);
}
