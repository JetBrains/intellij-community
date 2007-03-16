package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * Implements all abstractionos related to Groovy file
 * 
 * @author Ilya Sergey
 */
public class GroovyFile extends PsiFileBase {

  public GroovyFile(FileViewProvider viewProvider) {
    super(viewProvider, GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }
}

