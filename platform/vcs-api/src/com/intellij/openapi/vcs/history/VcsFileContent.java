/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

public interface VcsFileContent {
  @Nullable
  byte[] loadContent() throws IOException, VcsException;

  /**
   * Use {@link #loadContent()} instead
   */
  @Nullable
  @Deprecated
  byte[] getContent() throws IOException, VcsException;


  /**
   * @return the default charset to be used if detection by content fails. If null, the encoding from project settings will be used.
   */
  @Nullable
  default Charset getDefaultCharset() {
    return null;
  }
}
