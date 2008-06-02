package org.jetbrains.plugins.groovy.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2008
 */
public abstract class GrBaseGenerateAction extends BaseGenerateAction {
  public GrBaseGenerateAction(CodeInsightActionHandler handler) {
    super(handler);
  }

  protected String getCommandName() {
    return GroovyBundle.message("Constructor");
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    if (file instanceof PsiCompiledElement) return false;
    if (!GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType())) return false;
    
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClass targetClass = getTargetClass(editor, file);
    if (targetClass == null) return false;
    if (targetClass.isInterface()) return false; //?

    return true;
  }
}
