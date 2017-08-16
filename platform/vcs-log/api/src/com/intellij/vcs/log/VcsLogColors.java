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
package com.intellij.vcs.log;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;

public interface VcsLogColors {

  ColorKey MERGED_COMMIT = ColorKey.createColorKey("VCS_MERGED_COMMIT", new JBColor(Gray._128, Gray._96));

  ColorKey REFS_HEAD = ColorKey.createColorKey("VCS_REFS_HEAD", new JBColor(new Color(0xffd100), new Color(0xe1c731)));
  ColorKey REFS_LEAF = ColorKey.createColorKey("VCS_REFS_LEAF", new JBColor(new Color(0x8a2d6b), new Color(0xc31e8c)));
  ColorKey REFS_BRANCH = ColorKey.createColorKey("VCS_REFS_BRANCH", new JBColor(new Color(0x3cb45c), new Color(0x3cb45c)));
  ColorKey REFS_BRANCH_REF = ColorKey.createColorKey("VCS_REFS_BRANCH_REF", new JBColor(new Color(0x9f79b5), new Color(0x9f79b5)));
  ColorKey REFS_TAG = ColorKey.createColorKey("VCS_REFS_TAG", new JBColor(new Color(0x7a7a7a), new Color(0x999999)));

}
