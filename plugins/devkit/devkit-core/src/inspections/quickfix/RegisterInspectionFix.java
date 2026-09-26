// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author Dmitry Avdeev
 */
class RegisterInspectionFix implements IntentionAction {

  private final PsiClass myPsiClass;
  private final ExtensionPointName<? extends InspectionEP> myEp;

  RegisterInspectionFix(PsiClass psiClass, ExtensionPointName<? extends InspectionEP> ep) {
    myPsiClass = psiClass;
    myEp = ep;
  }

  @Override
  public @NotNull String getText() {
    return DevKitBundle.message("register.inspection.fix.name", myPsiClass.getName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return DevKitBundle.message("register.inspection.fix.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return !DumbService.isDumb(project);
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    PluginDescriptorChooser.show(project, editor, psiFile, element -> doFix(element, project, psiFile));
  }

  private void doFix(final DomFileElement<IdeaPlugin> selectedValue, final Project project, final PsiFile file) {
    Extension extension = WriteCommandAction.writeCommandAction(project, file).compute(() -> {
      final Extensions extensions = PluginDescriptorChooser.findOrCreateExtensionsForEP(selectedValue, myEp.getName());
      Extension e = extensions.addExtension(myEp.getName());
      XmlTag tag = e.getXmlTag();
      tag.setAttribute("implementationClass", myPsiClass.getQualifiedName());
      tag.setAttribute("language", "");
      return e;
    });
    PsiNavigateUtil.navigate(extension.getXmlTag());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return IntentionPreviewInfo.EMPTY;
  }
}
