/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ClassExtensionPoint;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class RegisterExtensionFix implements IntentionAction {
  private final PsiClass myExtensionClass;
  private final Set<ExtensionPointCandidate> myEPCandidates;

  public RegisterExtensionFix(PsiClass extensionClass, Set<ExtensionPointCandidate> epCandidates) {
    myExtensionClass = extensionClass;
    myEPCandidates = epCandidates;
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
    return !DumbService.isDumb(project);
  }

  @Override
  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PluginDescriptorChooser.show(project, editor, file, element -> doFix(editor, element));
  }

  private void doFix(Editor editor, final DomFileElement<IdeaPlugin> element) {
    if (myEPCandidates.size() == 1) {
      registerExtension(element, myEPCandidates.iterator().next());
    }
    else {
      final BaseListPopupStep<ExtensionPointCandidate> popupStep =
        new BaseListPopupStep<ExtensionPointCandidate>("Choose Extension Point", new ArrayList<>(myEPCandidates)) {
          @Override
          public PopupStep onChosen(ExtensionPointCandidate selectedValue, boolean finalChoice) {
            registerExtension(element, selectedValue);
            return FINAL_CHOICE;
          }
        };
      JBPopupFactory.getInstance().createListPopup(popupStep).showInBestPositionFor(editor);
    }
  }

  private void registerExtension(final DomFileElement<IdeaPlugin> selectedValue, final ExtensionPointCandidate candidate) {
    PsiElement navTarget = new WriteCommandAction<PsiElement>(selectedValue.getFile().getProject(), selectedValue.getFile()) {
      @Override
      protected void run(@NotNull Result<PsiElement> result) throws Throwable {
        Extensions extensions = PluginDescriptorChooser.findOrCreateExtensionsForEP(selectedValue, candidate.epName);
        Extension extension = extensions.addExtension(candidate.epName);
        XmlTag tag = extension.getXmlTag();
        PsiElement navTarget = null;
        String keyAttrName = KEY_MAP.get(candidate.beanClassName);
        if (keyAttrName != null) {
          XmlAttribute attr = tag.setAttribute(keyAttrName, "");
          navTarget = attr.getValueElement();
        }
        if (candidate.attributeName != null) {
          tag.setAttribute(candidate.attributeName, myExtensionClass.getQualifiedName());
        }
        else {
          XmlTag subTag = tag.createChildTag(candidate.tagName, null, myExtensionClass.getQualifiedName(), false);
          tag.addSubTag(subTag, false);
        }
        result.setResult(navTarget != null ? navTarget : extension.getXmlTag());
      }
    }.execute().throwException().getResultObject();
    PsiNavigateUtil.navigate(navTarget);
  }

  private static final Map<String, String> KEY_MAP = ContainerUtil.<String, String>immutableMapBuilder()
    .put(KeyedFactoryEPBean.class.getName(), "key")
    .put(KeyedLazyInstanceEP.class.getName(), "key")
    .put(FileTypeExtensionPoint.class.getName(), "filetype")
    .put(LanguageExtensionPoint.class.getName(), "language")
    .put(ClassExtensionPoint.class.getName(), "forClass")
    .build();


  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
