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
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;


public class WebDecompiler extends ConsoleDecompiler {

  private HashMap<String, File> mapInputFilenames = new HashMap<String, File>();

  private HashSet<String> setClassFiles = new HashSet<String>();

  private File root;

  public WebDecompiler(IFernflowerLogger logger, HashMap<String, Object> propertiesCustom) {
    super(logger, propertiesCustom);
  }

  @Override
  public void decompileContext(File root) {
    this.root = root;
    super.decompileContext(root);
  }

  @Override
  public void copyFile(String source, String destpath, String destfilename) {
    super.copyFile(source, destpath, destfilename);
    mapInputFilenames.put(destfilename, new File(getAbsolutePath(destpath), destfilename));
  }

  @Override
  public void saveFile(String path, String filename, String content) {
    super.saveFile(path, filename, content);

    mapInputFilenames.put(setClassFiles.contains(filename) ?
                          filename.substring(0, filename.lastIndexOf(".java")) + ".class" :
                          filename, new File(getAbsolutePath(path), filename));
  }

  @Override
  public void saveClassFile(String path, String qualifiedName, String entryName, String content) {
    setClassFiles.add(entryName);
    saveFile(path, entryName, content);
  }

  @Override
  public void closeArchive(String path, String archivename) {
    super.closeArchive(path, archivename);
    mapInputFilenames.put(archivename, new File(getAbsolutePath(path), archivename));
  }

  private String getAbsolutePath(String path) {
    return new File(root, path).getAbsolutePath();
  }

  public HashMap<String, File> getMapInputFilenames() {
    return mapInputFilenames;
  }
}

