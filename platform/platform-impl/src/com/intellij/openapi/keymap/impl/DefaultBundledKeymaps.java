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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformUtils;
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
    for (BundledKeymapBean bean : BundledKeymapBean.EP_NAME.getExtensionList()) {
      result.add(bean.file.replace("$OS$", os));
    }
    if (PlatformUtils.isAppCode()) {
      result.remove("Default for XWin.xml");
      result.remove("Default for GNOME.xml");
      result.remove("Default for KDE.xml");
    }
    return new ArrayList<>(result);
  }
}
