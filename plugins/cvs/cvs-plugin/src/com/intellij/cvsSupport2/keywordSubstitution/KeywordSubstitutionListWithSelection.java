package com.intellij.cvsSupport2.keywordSubstitution;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.ListWithSelection;

/**
 * author: lesya
 */
public class KeywordSubstitutionListWithSelection extends ListWithSelection<KeywordSubstitutionWrapper> {

  public KeywordSubstitutionListWithSelection() {

    add(KeywordSubstitutionWrapper.BINARY);
    add(KeywordSubstitutionWrapper.KEYWORD_COMPRESSION);
    add(KeywordSubstitutionWrapper.KEYWORD_EXPANSION);
    add(KeywordSubstitutionWrapper.KEYWORD_EXPANSION_LOCKER);
    add(KeywordSubstitutionWrapper.KEYWORD_REPLACEMENT);
    add(KeywordSubstitutionWrapper.NO_SUBSTITUTION);
  }

  public static KeywordSubstitutionListWithSelection createOnFileName(String fileName,
                                                                      CvsConfiguration config){
    KeywordSubstitutionListWithSelection result = new KeywordSubstitutionListWithSelection();
    boolean binary = FileTypeManager.getInstance().getFileTypeByFileName(fileName).isBinary();
    result.select(binary ? KeywordSubstitutionWrapper.BINARY :
        KeywordSubstitutionWrapper.getValue(config.DEFAULT_TEXT_FILE_SUBSTITUTION));
    return result;
    
  }
  
  public static KeywordSubstitutionListWithSelection createOnExtension(String extension){
    KeywordSubstitutionListWithSelection result = new KeywordSubstitutionListWithSelection();
    boolean binary = FileTypeManager.getInstance().getFileTypeByExtension(extension).isBinary();
    result.select(binary ? KeywordSubstitutionWrapper.BINARY : KeywordSubstitutionWrapper.KEYWORD_EXPANSION);
    return result;

  }

}
