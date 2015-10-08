/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.updates;

import com.intellij.openapi.updateSettings.impl.UpdatesInfo;
import com.intellij.openapi.util.JDOMUtil;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.net.URL;

public class InfoReader {
  @NotNull
  public static UpdatesInfo read(@NotNull String fileName) {
    return read(InfoReader.class.getResource(fileName));
  }

  @NotNull
  public static UpdatesInfo read(@NotNull URL url) {
    try (InputStream stream = url.openStream()) {
      return new UpdatesInfo(JDOMUtil.load(stream));
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
