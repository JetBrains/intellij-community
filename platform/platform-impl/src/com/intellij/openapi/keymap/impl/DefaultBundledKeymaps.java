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

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class DefaultBundledKeymaps implements BundledKeymapProvider {
  @NotNull
  @Override
  public List<String> getKeymapFileNames() {
    return Arrays.asList(
      "$default.xml",
      "Mac OS X 10.5+.xml",
      "Mac OS X.xml",
      "Emacs.xml",
      "Visual Studio.xml",
      "Default for XWin.xml",
      "Default for GNOME.xml",
      "Default for KDE.xml",
      "Eclipse.xml",
      "Eclipse (Mac OS X).xml",
      "NetBeans 6.5.xml"
    );
  }
}
