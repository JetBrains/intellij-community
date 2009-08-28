package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.util.Pair;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

public class ImportedFileType extends AbstractFileType {
  private final List<FileNameMatcher> myPatterns = new ArrayList<FileNameMatcher>();

  public ImportedFileType(final SyntaxTable syntaxTable, ExternalInfo copyFrom) {
    super(syntaxTable);
    if (copyFrom != null) {
      getExternalInfo().copy(copyFrom);
    }
  }

  public List<FileNameMatcher> getOriginalPatterns() {
    return myPatterns;
  }

  public void addPattern(FileNameMatcher pattern) {
    myPatterns.add(pattern);
  }

  public void readOriginalMatchers(final Element element) {
    Element mappingsElement = element.getChild(ELEMENT_EXTENSIONMAP);
    if (mappingsElement != null) {
      List<Pair<FileNameMatcher,String>> list = AbstractFileType.readAssociations(mappingsElement);
      for (Pair<FileNameMatcher, String> pair : list) {
        addPattern(pair.getFirst());
      }
    }

  }
}
