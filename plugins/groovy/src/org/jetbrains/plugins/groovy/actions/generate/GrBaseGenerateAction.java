package org.jetbrains.plugins.groovy.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.GroovyBundle;

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

//  @Nullable
//  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
//    int offset = editor.getCaretModel().getOffset();
//    PsiElement element = file.findElementAt(offset);
//    if (element == null) return null;
//
//    PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
//
//    if (target == null) {
//      GroovyFile targetClass = PsiTreeUtil.getParentOfType(element, GroovyFile.class);
//      if (targetClass != null) {
//        if (targetClass.isScript()) target = targetClass.getScriptClass();
//      }
//    }
//    return target;
//  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    if (file instanceof PsiCompiledElement) return false;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClass targetClass = getTargetClass(editor, file);
    if (targetClass == null) return false;
    if (targetClass.isInterface()) return false; //?

    return true;
  }
}
