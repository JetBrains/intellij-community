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
package com.intellij.util.io.fs;

import com.intellij.openapi.util.io.FileUtil;

public class FilePath {
  private final String myPath;

  public FilePath(String path) {
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  @Override
  public String toString() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return FileUtil.pathsEqual(myPath, ((FilePath)o).myPath);
  }

  @Override
  public int hashCode() {
    return FileUtil.pathHashCode(myPath);
  }
}
