// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.ui.JBColor;

import java.awt.*;

public class VcsLogStandardColors {
  public static final class Refs {
    public static final Color TIP = new JBColor(new Color(0xffd100), new Color(0xe1c731));
    public static final Color LEAF = new JBColor(new Color(0x8a2d6b), new Color(0xc31e8c));
    public static final Color BRANCH = new JBColor(new Color(0x3cb45c), new Color(0x3cb45c));
    public static final Color BRANCH_REF = new JBColor(new Color(0x9f79b5), new Color(0x9f79b5));
    public static final Color TAG = new JBColor(new Color(0x7a7a7a), new Color(0x999999));
  }
}
