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

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DefaultBundledKeymaps implements BundledKeymapProvider {
  @NotNull
  @Override
  public List<String> getKeymapFileNames() {
    ArrayList<String> result = new ArrayList<>();
    result.add("$default.xml");
    result.add("Mac OS X 10.5+.xml");
    result.add("Mac OS X.xml");
    if (!PlatformUtils.isAppCode()) {
      result.add("Default for XWin.xml");
      result.add("Default for GNOME.xml");
      result.add("Default for KDE.xml");
    }
    result.add("Emacs.xml");
    result.add("Sublime Text.xml");
    result.add("Sublime Text (Mac OS X).xml");
    return result;
  }
}
