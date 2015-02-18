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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class ImportedFileType extends AbstractFileType {
  private final List<FileNameMatcher> myPatterns = new ArrayList<FileNameMatcher>();

  public ImportedFileType(@NotNull SyntaxTable syntaxTable) {
    super(syntaxTable);
  }

  public List<FileNameMatcher> getOriginalPatterns() {
    return myPatterns;
  }

  public void addPattern(FileNameMatcher pattern) {
    myPatterns.add(pattern);
  }

  public void readOriginalMatchers(@NotNull Element element) {
    Element mappingsElement = element.getChild(ELEMENT_EXTENSIONMAP);
    if (mappingsElement != null) {
      for (Pair<FileNameMatcher, String> pair : AbstractFileType.readAssociations(mappingsElement)) {
        addPattern(pair.getFirst());
      }
    }
  }
}
