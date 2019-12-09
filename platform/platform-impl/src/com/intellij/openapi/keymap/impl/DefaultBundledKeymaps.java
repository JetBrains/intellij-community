/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** @deprecated Use {@link BundledKeymapBean} instead. */
@ApiStatus.ScheduledForRemoval
@Deprecated
public class DefaultBundledKeymaps implements BundledKeymapProvider {
  @NotNull
  @Override
  public List<String> getKeymapFileNames() {
    Set<String> result = new LinkedHashSet<>();
    String os = SystemInfo.isMac ? "macos" :
                SystemInfo.isWindows ? "windows" :
                SystemInfo.isLinux ? "linux" : "other";
    boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();
    for (BundledKeymapBean bean : BundledKeymapBean.EP_NAME.getExtensionList()) {
      IdeaPluginDescriptor plugin = bean.getPluginId() == null ? null : PluginManagerCore.getPlugin(bean.getPluginId());
      String keymapName = FileUtil.getNameWithoutExtension(bean.file);
      if (bean.file.contains("$OS$")) {
        // add all OS-specific
        result.add(bean.file.replace("$OS$", os));
      }
      else if (headless ||
               plugin != null && !plugin.isBundled() && !isBundledMacOSKeymap(keymapName) ||
               !isBundledKeymapHidden(keymapName)) {
        // filter out bundled keymaps for other systems, but allow them via non-bundled plugins
        // also skip non-bundled known macOS keymaps on non-macOS systems
        result.add(bean.file);
      }
    }
    return new ArrayList<>(result);
  }

  public static boolean isBundledKeymapHidden(@Nullable String keymapName) {
    if (SystemInfo.isWindows || SystemInfo.isMac) {
      if ("Default for XWin".equals(keymapName) ||
          "Default for GNOME".equals(keymapName) ||
          "Default for KDE".equals(keymapName)) return true;
    }
    if (isBundledMacOSKeymap(keymapName)) return true;
    return false;
  }

  private static boolean isBundledMacOSKeymap(@Nullable String keymapName) {
    if (!SystemInfo.isMac) {
      if ("Mac OS X".equals(keymapName) ||
          "Mac OS X 10.5+".equals(keymapName) ||
          "Eclipse (Mac OS X)".equals(keymapName) ||
          "Sublime Text (Mac OS X)".equals(keymapName)) {
        return true;
      }
    }
    return false;
  }
}
