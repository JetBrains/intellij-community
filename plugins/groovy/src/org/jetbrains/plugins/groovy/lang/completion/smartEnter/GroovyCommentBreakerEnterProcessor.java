package org.jetbrains.plugins.groovy.lang.completion.smartEnter;

import com.intellij.codeInsight.editorActions.smartEnter.EnterProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.07.2008
 */
public class GroovyCommentBreakerEnterProcessor implements EnterProcessor {
    public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
