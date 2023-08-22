// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jps.impl;

import com.intellij.openapi.extensions.ExtensionPointName;

public final class JpsPluginBean {
  public static final ExtensionPointName<JpsPluginBean> EP_NAME = new ExtensionPointName<>("com.intellij.jps.plugin");
}
