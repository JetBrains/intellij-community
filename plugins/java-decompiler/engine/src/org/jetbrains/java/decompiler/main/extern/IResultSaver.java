/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.extern;

import java.util.jar.Manifest;

public interface IResultSaver {
  void saveFolder(String path);

  void copyFile(String source, String path, String entryName);

  void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping);

  void createArchive(String path, String archiveName, Manifest manifest);

  void saveDirEntry(String path, String archiveName, String entryName);

  void copyEntry(String source, String path, String archiveName, String entry);

  void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content);

  void closeArchive(String path, String archiveName);
}
