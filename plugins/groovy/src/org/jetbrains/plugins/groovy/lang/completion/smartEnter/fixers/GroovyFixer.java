package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.07.2008
 */
public interface GroovyFixer {
    void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException;
}
