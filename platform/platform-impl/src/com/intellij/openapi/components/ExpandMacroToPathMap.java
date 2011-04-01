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
package com.intellij.openapi.components;

import com.intellij.openapi.util.text.StringUtil;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class ExpandMacroToPathMap extends PathMacroMap {

  public void addMacroExpand(String macroName, String path) {
    String replacement = quotePath(path);
    String withSlash = StringUtil.trimEnd(replacement, "/") + "/";
    put("$" + macroName + "$//", withSlash);
    put("$" + macroName + "$/", withSlash);
    put("$" + macroName + "$", replacement);
  }

  public String substitute(String text, boolean caseSensitive) {
    if (text == null) return null;
    for (Map.Entry<String, String> entry : entries()) {
      // when replacing macros with actual paths the replace utility may be used as always 'case-sensitive'
      // for case-insensitive file systems there will be no unnecesary toLowerCase() transforms. 
      text = StringUtil.replace(text, entry.getKey(), entry.getValue(), false);
    }
    return text;
  }

}
