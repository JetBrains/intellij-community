// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.util.ui.GrayFilter;
import com.intellij.util.ui.UIUtil;

final class TextGrayFilterConfig extends GrayFilterConfig {
  @Override
  protected GrayFilter getGrayFilter() {
    return (GrayFilter)UIUtil.getTextGrayFilter();
  }

  @Override
  protected String getGrayFilterKey() {
    return "text.grayFilter";
  }
}
