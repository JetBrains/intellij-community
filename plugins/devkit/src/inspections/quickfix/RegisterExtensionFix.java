/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author yole
 */
public class RegisterExtensionFix implements IntentionAction {
  private final PsiClass myExtensionClass;
  private final String myEPName;

  public RegisterExtensionFix(PsiClass extensionClass, String epName) {
    myExtensionClass = extensionClass;
    myEPName = epName;
  }

  @NotNull
  @Override
  public String getText() {
    return "Register extension";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    RegisterInspectionFix.choosePluginDescriptor(project, editor, file, new Consumer<DomFileElement<IdeaPlugin>>() {
      @Override
      public void consume(DomFileElement<IdeaPlugin> element) {
        doFix(element);
      }
    });
  }

  private void doFix(final DomFileElement<IdeaPlugin> element) {
    Extension extension = new WriteCommandAction<Extension>(element.getFile().getProject(), element.getFile()) {
      @Override
      protected void run(Result<Extension> result) throws Throwable {
        Extensions extensions = RegisterInspectionFix.getExtension(element.getRootElement(), myEPName);
        Extension extension = extensions.addExtension(myEPName);
        XmlTag tag = extension.getXmlTag();
        tag.setAttribute("implementation", myExtensionClass.getQualifiedName());
        result.setResult(extension);
      }
    }.execute().throwException().getResultObject();
    PsiNavigateUtil.navigate(extension.getXmlTag());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
