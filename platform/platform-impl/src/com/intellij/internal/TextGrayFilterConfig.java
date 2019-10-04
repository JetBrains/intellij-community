// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.util.ui.UIUtil;

public final class TextGrayFilterConfig extends GrayFilterConfig {
  @Override
  protected UIUtil.GrayFilter getGrayFilter() {
    return (UIUtil.GrayFilter)UIUtil.getTextGrayFilter();
  }

  @Override
  protected String getGrayFilterKey() {
    return "text.grayFilter";
  }
}
