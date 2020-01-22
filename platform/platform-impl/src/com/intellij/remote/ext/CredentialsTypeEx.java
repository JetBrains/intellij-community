// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.remote.CredentialsType;
import org.jetbrains.annotations.Nls;

public abstract class CredentialsTypeEx<T> extends CredentialsType<T> {

  protected CredentialsTypeEx(@Nls(capitalization = Nls.Capitalization.Title) String name, String prefix) {
    super(name, prefix);
  }
}
