// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * Name file as "Your scheme name.xml" and put it to keymaps.
 * Since 2017.1 file name must be equal to scheme name and no wrapping "component" tag.
 */
public interface BundledKeymapProvider {
  ExtensionPointName<BundledKeymapProvider> EP_NAME = ExtensionPointName.create("com.intellij.bundledKeymapProvider");

  @NotNull
  List<String> getKeymapFileNames();

  default <R> R load(@NotNull String key, @NotNull Function<InputStream, R> consumer) throws IOException {
    try (InputStream stream = BundledKeymapProvider.class.getResourceAsStream("/keymaps/" + key)) {
      return consumer.apply(stream);
    }
  }

  /**
   * Returns the name of the keymap stored in the given file.
   */
  default String getKeyFromFileName(String filename) {
    return FileUtilRt.getNameWithoutExtension(filename);
  }
}
