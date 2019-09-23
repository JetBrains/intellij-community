// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author gregsh
 */
public class BundledKeymapBean extends AbstractExtensionPointBean {

  public static final ExtensionPointName<BundledKeymapBean> EP_NAME = ExtensionPointName.create("com.intellij.bundledKeymap");

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
