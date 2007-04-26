/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans for needed files in path
 *
 * @author Ilya.Sergey
 */
public class FileScanner {

  /**
   * @param path       Path to base file (often it`s a directory to scan)
   * @param pattern    Pattern to match files
   * @param dirAllowed true if directory is allowed.
   * @return List of files
   * @throws java.io.FileNotFoundException if file not found
   */
  public static List<File> scan(final String path, final String pattern, final boolean dirAllowed) throws FileNotFoundException {
    List<File> myFiles = new ArrayList<File>();
    File baseFile = new File(path);
    if (!baseFile.exists()) {
      throw new FileNotFoundException("File " + path + " doesn`t exist!");
    }
    scanForFiles(myFiles, baseFile, pattern, dirAllowed);
    return myFiles;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void scanForFiles(final List<File> files, final File f, final String pattern, final boolean dirAllowed) {
    // recursively scan for all subdirectories
    if (f.isDirectory()) {
      for (File file : f.listFiles()) {
        if (!file.isDirectory()) {
          String path = file.getAbsolutePath();
          if (!path.contains(".svn") &&
                  !path.contains(".cvs") &&
                  path.matches(pattern)) {
            files.add(file);
          }
        } else {
          if (dirAllowed) {
            files.add(file);
          }
          scanForFiles(files, file, pattern, dirAllowed);
        }
      }
    }
  }

}
