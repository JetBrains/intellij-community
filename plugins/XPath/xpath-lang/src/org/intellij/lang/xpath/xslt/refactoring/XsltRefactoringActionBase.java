/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.refactoring;

import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public abstract class XsltRefactoringActionBase extends AnAction implements HookedAction, RefactoringActionHandler {
    protected AnAction hookedAction;
    protected boolean myExplicitInjectedContext;

    protected XsltRefactoringActionBase() {
        myExplicitInjectedContext = true;
    }

    public XsltRefactoringActionBase(AnAction hookedAction) {
        setHookedAction(hookedAction);
    }

    public void setHookedAction(AnAction hookedAction) {
        this.hookedAction = hookedAction;
        copyFrom(hookedAction);
        setEnabledInModalContext(hookedAction.isEnabledInModalContext());
    }

    public boolean displayTextInToolbar() {
        return hookedAction.displayTextInToolbar();
    }

    public final void update(AnActionEvent e) {
        super.update(e);
        hookedAction.update(e);
        if (!e.getPresentation().isEnabled()) {
            updateImpl(e);
        }
    }

    protected void updateImpl(AnActionEvent e) {
        final PsiFile file = DataKeys.PSI_FILE.getData(e.getDataContext());
        if (file != null) {
            final PsiFile context = PsiTreeUtil.getContextOfType(file, XmlFile.class, false);
            if (context != null && XsltSupport.isXsltFile(context)) {
                final Editor editor = DataKeys.EDITOR.getData(e.getDataContext());
                e.getPresentation().setEnabled(editor != null);
            }
        }
    }

    public void setDefaultIcon(boolean b) {
        hookedAction.setDefaultIcon(b);
    }

    public boolean isDefaultIcon() {
        return hookedAction.isDefaultIcon();
    }

    public void setInjectedContext(boolean worksInInjected) {
        hookedAction.setInjectedContext(worksInInjected);
    }

    public boolean isInInjectedContext() {
        return myExplicitInjectedContext || hookedAction.isInInjectedContext();
    }

    public AnAction getHookedAction() {
        return hookedAction;
    }

    public void actionPerformed(AnActionEvent e) {
        final Editor editor = DataKeys.EDITOR.getData(e.getDataContext());
        final PsiFile file = DataKeys.PSI_FILE.getData(e.getDataContext());
        final Project project = DataKeys.PROJECT.getData(e.getDataContext());

        if (project != null && editor != null && file != null) {
            final PsiFile context = PsiTreeUtil.getContextOfType(file, XmlFile.class, false);
            if (context != null && XsltSupport.isXsltFile(context)) {
                invoke(project, editor, file, e.getDataContext());
            } else {
                getHookedAction().actionPerformed(e);
            }
        } else {
            getHookedAction().actionPerformed(e);
        }
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final int offset = editor.getCaretModel().getOffset();

        final XmlAttribute context = PsiTreeUtil.getContextOfType(file, XmlAttribute.class, true);
        if (context != null) {
            if (actionPerformedImpl(file, editor, context, offset)) {
                return;
            }
        }

        final String message = getErrorMessage(editor, file, context);
        Messages.showErrorDialog(editor.getProject(), "Cannot perform refactoring.\n" +
                (message != null ? message : getRefactoringName() + " is not available in the current context."), "XSLT - " + getRefactoringName());
    }

    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public String getErrorMessage(Editor editor, PsiFile file, XmlAttribute context) {
        return null;
    }

    public abstract String getRefactoringName();

    protected abstract boolean actionPerformedImpl(PsiFile file, Editor editor, XmlAttribute context, int offset);
}