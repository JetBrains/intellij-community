/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.jetbrains.idea.devkit.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class GenerateComponentExternalizationAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.actions.GenerateComponentExternalizationAction");

  @NonNls private final static String BASE_COMPONENT = "com.intellij.openapi.components.BaseComponent";
  @NonNls private final static String JDOM_EXTERN = "com.intellij.openapi.util.JDOMExternalizable";

  public void actionPerformed(AnActionEvent e) {
    final PsiClass target = getComponentInContext(e.getDataContext());
    assert target != null;

    final PsiElementFactory factory = target.getManager().getElementFactory();
    final CodeStyleManager formatter = target.getManager().getCodeStyleManager();


    Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              final PsiReferenceList implList = target.getImplementsList();
              assert implList != null;

              implList.add(factory.createReferenceElementByFQClassName(JDOM_EXTERN, target.getResolveScope()));
              PsiMethod read = factory.createMethodFromText(
                "public void readExternal(org.jdom.Element element) throws com.intellij.openapi.util.InvalidDataException { com.intellij.openapi.util.DefaultJDOMExternalizer.readExternal(this, element); }",
                target
              );

              read = (PsiMethod)formatter.reformat(target.add(read));
              formatter.shortenClassReferences(read);

              PsiMethod write = factory.createMethodFromText(
                "public void writeExternal(org.jdom.Element element) throws com.intellij.openapi.util.WriteExternalException { com.intellij.openapi.util.DefaultJDOMExternalizer.writeExternal(this, element); }",
                target
              );

              write = (PsiMethod)formatter.reformat(target.add(write));
              formatter.shortenClassReferences(write);
            }
            catch (IncorrectOperationException e1) {
              LOG.error(e1);
            }
          }
        });
      }
    };

    CommandProcessor.getInstance().executeCommand(target.getProject(), runnable, "Implement Externalizable", null);
  }

  @Nullable
  private PsiClass getComponentInContext(DataContext context) {
    Editor editor = (Editor)context.getData(DataConstants.EDITOR);
    Project project = (Project)context.getData(DataConstants.PROJECT);
    if (editor == null || project == null) return null;

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    PsiFile file = (PsiFile)context.getData(DataConstants.PSI_FILE);
    if (file == null) return null;

    PsiClass contextClass = PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), PsiClass.class, false);
    if (contextClass == null || contextClass.isEnum() || contextClass.isInterface() || contextClass instanceof PsiAnonymousClass) {
      return null;
    }

    PsiClass componentClass = file.getManager().findClass(BASE_COMPONENT, file.getResolveScope());
    if (componentClass == null || !contextClass.isInheritor(componentClass, true)) return null;

    PsiClass externClass = file.getManager().findClass(JDOM_EXTERN, file.getResolveScope());
    if (externClass == null || contextClass.isInheritor(externClass, true)) return null;


    return contextClass;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final PsiClass target = getComponentInContext(e.getDataContext());

    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(target != null);
    presentation.setVisible(target != null);
  }
}
