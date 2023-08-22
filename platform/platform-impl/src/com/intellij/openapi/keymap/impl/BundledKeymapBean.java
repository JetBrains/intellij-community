// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

public final class BundledKeymapBean {
  public static final ExtensionPointName<BundledKeymapBean> EP_NAME = new ExtensionPointName<>("com.intellij.bundledKeymap");

  /**
   * Keymap resource name is as follows: /keymaps/$file$.
   * Optionally, <b>$OS$</b> macro can be used (values: macos, windows, linux or other).
   * <p/>
   * Example:<br>
   * <code>&lt;bundledKeymap file="$OS$/abc.xml"&gt;</code><br>
   * on Windows points to <code>/keymaps/windows/abc.xml</code> resource,<br>
   * on macOS points to <code>/keymaps/macos/abc.xml</code> resource.
   */
  @Attribute
  public String file;
}
