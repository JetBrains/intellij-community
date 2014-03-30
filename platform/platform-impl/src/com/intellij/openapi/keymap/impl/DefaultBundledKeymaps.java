/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.Arrays;
import java.util.List;

public class DefaultBundledKeymaps implements BundledKeymapProvider {
  public List<String> getKeymapFileNames() {
    return Arrays.asList(
      "Keymap_Default.xml",
      "Keymap_Mac.xml",
      "Keymap_MacClassic.xml",
      "Keymap_Emacs.xml",
      "Keymap_VisualStudio.xml",
      "Keymap_XWin.xml",
      "Keymap_GNOME.xml",
      "Keymap_KDE.xml",
      "Keymap_Eclipse.xml",
      "Keymap_EclipseMac.xml",
      "Keymap_Netbeans.xml"
    );
  }
}
