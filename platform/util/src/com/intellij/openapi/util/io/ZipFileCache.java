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
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.ZipFile;

/** @deprecated use {@link ZipFile#ZipFile(String)} and {@link ZipFile#close()} instead (to be removed in IDEA 17) */
@SuppressWarnings("unused")
public class ZipFileCache {
  @NotNull
  public static ZipFile acquire(@NotNull String path) throws IOException {
    return new ZipFile(path);
  }

  public static void release(@NotNull ZipFile file) {
    try { file.close(); }
    catch (IOException ignored) { }
  }

  public static void reset(Collection<String> paths) { }

  public static void stopBackgroundThread() { }
}
