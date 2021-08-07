// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import org.jetbrains.annotations.ApiStatus;

import static com.intellij.openapi.util.registry.Registry.is;

/**
 * Temporary utility class for migration to the new UI.
 * Do not use this class for plugin development.
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ExperimentalUI {
  public static boolean isNewUI() {
    return is("ide.experimental.ui");
  }

  public static boolean isNewToolWindowsStripes() {
    return isNewUI() || is("ide.experimental.ui.toolwindow.stripes");
  }

  public static boolean isNewEditorTabs() {
    return isNewUI() || is("ide.experimental.ui.editor.tabs");
  }
}
