/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.ui.playback;

import com.intellij.util.containers.HashMap;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class PathMacro {
  
  private Map<String, File> myMap = new HashMap<>();

  public PathMacro setScriptDir(File dir) {
    myMap.put("\\{script\\.dir\\}", dir);
    return this;
  }

  public PathMacro setBaseDir(File dir) {
    myMap.put("\\{base\\.dir\\}", dir);
    return this;
  }
  
  public File resolveFile(String path, File defaultDir) {
    Set<String> macros = myMap.keySet();
    String actualtPath = path;
    for (Iterator<String> iterator = macros.iterator(); iterator.hasNext(); ) {
      String each = iterator.next();
      actualtPath = actualtPath.replaceAll(each, myMap.get(each).getAbsolutePath());
    }


    File file = new File(actualtPath);
    if (!file.isAbsolute()) {
      file = new File(defaultDir, actualtPath);
    }

    return file;
  }

}
