/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.vcs.FilePath;

import java.util.Comparator;

public class FilePathByPathComparator implements Comparator<FilePath> {
  private final static FilePathByPathComparator ourInstance = new FilePathByPathComparator();

  public static FilePathByPathComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(FilePath o1, FilePath o2) {
    return o1.getPath().compareTo(o2.getPath());
  }
}
