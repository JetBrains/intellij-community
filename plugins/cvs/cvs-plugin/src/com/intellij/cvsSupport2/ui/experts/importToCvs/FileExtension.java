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
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;

/**
 * author: lesya
 */
public class FileExtension {
  private final String myExtension;
  private final KeywordSubstitutionListWithSelection myKeywordSubstitution;

  public FileExtension(String extension) {
    myExtension = extension;
    myKeywordSubstitution = KeywordSubstitutionListWithSelection.createOnExtension(extension);
  }

  public FileExtension(String extension, String substitution) {
    this(extension);
    setKeywordSubstitution(KeywordSubstitutionWrapper.getValue(substitution));
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileExtension)) return false;

    final FileExtension fileExtension = (FileExtension)o;

    if (myExtension != null ? !myExtension.equals(fileExtension.myExtension) : fileExtension.myExtension != null) return false;

    return true;
  }

  public int hashCode() {
    return (myExtension != null ? myExtension.hashCode() : 0);
  }

  public void setKeywordSubstitution(KeywordSubstitutionWrapper substitution) {
    myKeywordSubstitution.select(substitution);
  }

  public KeywordSubstitutionListWithSelection getKeywordSubstitutionsWithSelection() {
    return myKeywordSubstitution;
  }

  public String getExtension() {
    return myExtension;
  }

  public KeywordSubstitutionWrapper getKeywordSubstitution() {
    return getKeywordSubstitutionsWithSelection().getSelection();
  }
}
