/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;

import java.awt.*;

public class VcsLogStandardColors {
  public static class Refs {
    public static final Color TIP = new JBColor(new Color(0xedea74), new Color(113, 111, 64));
    public static final Color LEAF = new JBColor(new Color(0xf478cc), new Color(0x6e2857));
    public static final Color BRANCH = new JBColor(new Color(0x75eec7), new Color(0x0D6D4F));
    public static final Color BRANCH_REF = new JBColor(new Color(0xbcbcfc), new Color(0xbcbcfc).darker().darker());
    public static final Color TAG =
      new JBColor(ColorUtil.darker(new Color(255, 255, 255), 1), ColorUtil.brighter(new Color(60, 63, 65), 3));
  }
}
