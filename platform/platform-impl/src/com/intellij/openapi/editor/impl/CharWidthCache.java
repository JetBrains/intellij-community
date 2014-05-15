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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.containers.IntIntHashMap;
import org.intellij.lang.annotations.JdkConstants;

/**
 * @author peter
 */
public class CharWidthCache {
  private final Editor myEditor;
  private final IntIntHashMap myCache = new IntIntHashMap();

  public CharWidthCache(Editor editor) {
    myEditor = editor;
  }

  public int charWidth(char c, @JdkConstants.FontStyle int fontType) {
    int key = c + fontType * (Character.MAX_VALUE + 1);
    int width = myCache.get(key);
    if (width < 0) {
      myCache.put(key, width = EditorUtil.charWidth(c, fontType, myEditor));
    }
    return width;
  }

}
