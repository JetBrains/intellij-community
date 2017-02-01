/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
    try (InputStream stream = URLUtil.openResourceStream(new URL("file:///keymaps/" + key))) {
      return consumer.apply(stream);
    }
  }
}
