/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
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

  @NotNull
  @Override
  public String getText() {
    return "Register inspection";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return DevKitBundle.message("inspections.component.not.registered.quickfix.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !DumbService.isDumb(project);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    PluginDescriptorChooser.show(project, editor, file, element -> doFix(element, project, file));
  }

  private void doFix(final DomFileElement<IdeaPlugin> selectedValue, final Project project, final PsiFile file) {
    Extension extension = WriteCommandAction.writeCommandAction(project, file).compute(() -> {
      final Extensions extensions = PluginDescriptorChooser.findOrCreateExtensionsForEP(selectedValue, myEp.getName());
      Extension e = extensions.addExtension(myEp.getName());
      XmlTag tag = e.getXmlTag();
      tag.setAttribute("implementationClass", myPsiClass.getQualifiedName());
      return e;
    });
    PsiNavigateUtil.navigate(extension.getXmlTag());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
