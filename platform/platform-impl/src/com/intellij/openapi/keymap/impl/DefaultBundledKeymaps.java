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

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
    PluginId coreId = PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID);
    boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();
    for (BundledKeymapBean bean : BundledKeymapBean.EP_NAME.getExtensionList()) {
      if (bean.file.contains("$OS$")) {
        // add all OS-specific
        result.add(bean.file.replace("$OS$", os));
      }
      else if (headless || !coreId.equals(bean.getPluginId()) || isCoreKeymapAccepted(bean.file)) {
        // filter out bundled keymaps for other systems, but allow them via plugins
        result.add(bean.file);
      }
    }
    return new ArrayList<>(result);
  }

  private static boolean isCoreKeymapAccepted(String file) {
    if (SystemInfo.isWindows || SystemInfo.isMac) {
      if ("Default for XWin.xml".equals(file) ||
          "Default for GNOME.xml".equals(file) ||
          "Default for KDE.xml".equals(file)) return false;
    }
    if (!SystemInfo.isMac) {
      if ("Mac OS X.xml".equals(file) ||
          "Mac OS X 10.5+.xml".equals(file) ||
          "Eclipse (Mac OS X).xml".equals(file) ||
          "Sublime Text (Mac OS X).xml".equals(file)) {
        return false;
      }
    }
    return true;
  }
}
