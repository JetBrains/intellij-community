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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class ImportedFileType extends AbstractFileType {
  private FileNameMatcher[] myPatterns;

  public ImportedFileType(@NotNull SyntaxTable syntaxTable) {
    super(syntaxTable);
  }

  @Nullable
  public FileNameMatcher[] getOriginalPatterns() {
    return myPatterns;
  }

  public boolean hasPattern(@NotNull FileNameMatcher matcher) {
    return myPatterns != null && ArrayUtil.contains(matcher, myPatterns);
  }

  public void readOriginalMatchers(@NotNull Element element) {
    Element mappingsElement = element.getChild(ELEMENT_EXTENSION_MAP);
    if (mappingsElement != null) {
      List<Pair<FileNameMatcher, String>> associations = AbstractFileType.readAssociations(mappingsElement);
      if (!associations.isEmpty()) {
        myPatterns = new FileNameMatcher[associations.size()];
        for (int i = 0; i < myPatterns.length; i++) {
          myPatterns[i] = associations.get(i).getFirst();
        }
      }
    }
  }
}
