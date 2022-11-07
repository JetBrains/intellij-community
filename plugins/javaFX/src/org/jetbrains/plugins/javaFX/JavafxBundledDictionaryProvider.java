// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX;

import com.intellij.spellchecker.BundledDictionaryProvider;

final class JavafxBundledDictionaryProvider implements BundledDictionaryProvider {
  @Override
  public String[] getBundledDictionaries() {
    return new String[]{"/dictionaries/javafx.dic"};
  }
}
