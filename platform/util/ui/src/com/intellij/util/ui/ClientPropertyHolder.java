// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NonNls;

public interface ClientPropertyHolder {

  void putClientProperty(@NonNls String key, @NonNls Object value);

}