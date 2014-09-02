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

public interface IDecompilatSaver {

  void copyFile(String source, String destpath, String destfilename);

  void saveFolder(String path);

  void saveClassFile(String path, String qualifiedName, String entryName, String content);

  void saveFile(String path, String filename, String content);

  void createArchive(String path, String archivename, Manifest manifest);

  void saveClassEntry(String path, String archivename, String qualifiedName, String entryName, String content);

  void saveEntry(String path, String archivename, String entryName, String content);

  void copyEntry(String source, String destpath, String archivename, String entry);

  void closeArchive(String path, String archivename);
}
