package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;

/**
 * author: lesya
 */
public class FileExtension {
  private final String myExtension;
  private KeywordSubstitutionListWithSelection myKeywordSubstitution;

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
    return (KeywordSubstitutionWrapper)getKeywordSubstitutionsWithSelection().getSelection();
  }


}
