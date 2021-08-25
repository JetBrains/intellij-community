// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.NlsSafe;

public interface RemoteSdkPropertiesPaths {
  @NlsSafe
  String getInterpreterPath();

  @NlsSafe
  String getHelpersPath();
}
