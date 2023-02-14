// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.util.ui.UIUtil;

final class TextGrayFilterConfig extends GrayFilterConfig {

  @Override
  protected UIUtil.GrayFilter getGrayFilter() {
    return (UIUtil.GrayFilter)UIUtil.getTextGrayFilter();
  }

  @Override
  protected String getGrayFilterKey() {
    return "text.grayFilter";
  }
}
