// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit;

import com.intellij.spellchecker.BundledDictionaryProvider;

public class DevKitBundledDictionaryProvider implements BundledDictionaryProvider {

  @Override
  public String[] getBundledDictionaries() {
    return new String[]{"/spellchecker/devkit.dic"};
  }
}
