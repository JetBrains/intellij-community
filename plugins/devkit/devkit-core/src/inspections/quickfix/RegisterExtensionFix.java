// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LanguageExtensionPoint;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class RegisterExtensionFix implements IntentionAction {
  private final PsiClass myExtensionClass;
  private final Set<? extends ExtensionPointCandidate> myEPCandidates;

  public RegisterExtensionFix(PsiClass extensionClass, Set<? extends ExtensionPointCandidate> epCandidates) {
    myExtensionClass = extensionClass;
    myEPCandidates = epCandidates;
  }

  @NotNull
  @Override
  public String getText() {
    return DevKitBundle.message("register.extension.fix.name");
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
        new BaseListPopupStep<>(DevKitBundle.message("register.extension.fix.popup.title"), new ArrayList<>(myEPCandidates)) {
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
    PsiElement navTarget =
      WriteCommandAction.writeCommandAction(selectedValue.getFile().getProject(), selectedValue.getFile()).compute(() -> {
        Extensions extensions = PluginDescriptorChooser.findOrCreateExtensionsForEP(selectedValue, candidate.epName);
        Extension extension = extensions.addExtension(candidate.epName);
        XmlTag tag = extension.getXmlTag();
        PsiElement target = null;
        String keyAttrName = KEY_MAP.get(candidate.beanClassName);
        if (keyAttrName != null) {
          XmlAttribute attr = tag.setAttribute(keyAttrName, "");
          target = attr.getValueElement();
        }
        if (candidate.attributeName != null) {
          tag.setAttribute(candidate.attributeName, myExtensionClass.getQualifiedName());
        }
        else {
          XmlTag subTag = tag.createChildTag(candidate.tagName, null, myExtensionClass.getQualifiedName(), false);
          tag.addSubTag(subTag, false);
        }
        return target != null ? target : extension.getXmlTag();
      });
    PsiNavigateUtil.navigate(navTarget);
  }

  @NonNls
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
