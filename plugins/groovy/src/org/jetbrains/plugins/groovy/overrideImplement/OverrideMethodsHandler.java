package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 11.09.2007
 */
public class OverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    GroovyOverrideImplementUtil.invokeOverrideImplement(project, editor, file, false);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
