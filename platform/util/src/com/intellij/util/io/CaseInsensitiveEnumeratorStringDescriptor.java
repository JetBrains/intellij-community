// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.text.StringUtil;

public final class CaseInsensitiveEnumeratorStringDescriptor extends EnumeratorStringDescriptor {
  @Override
  public int getHashCode(String value) {
    return StringUtil.stringHashCodeInsensitive(value);
  }

  @Override
  public boolean isEqual(String val1, String val2) {
    return val1.equalsIgnoreCase(val2);
  }
}
