// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
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
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class RegisterExtensionFix extends IntentionAndQuickFixAction {
  private final SmartPsiElementPointer<PsiClass> myExtensionClassPointer;
  private final Set<? extends ExtensionPointCandidate> myEPCandidates;

  public RegisterExtensionFix(PsiClass extensionClass, Set<? extends ExtensionPointCandidate> epCandidates) {
    myExtensionClassPointer = SmartPointerManager.createPointer(extensionClass);
    myEPCandidates = epCandidates;
  }

  @NotNull
  @Override
  public String getText() {
    return DevKitBundle.message("register.extension.fix.name");
  }

  @Override
  public @IntentionName @NotNull String getName() {
    return getText();
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
  public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
    PluginDescriptorChooser.show(project, editor, file, element -> doFix(editor, element));
  }

  private void doFix(Editor editor, final DomFileElement<IdeaPlugin> element) {
    PsiClass extensionClass = myExtensionClassPointer.getElement();
    if (extensionClass == null || !extensionClass.isValid()) return;

    if (myEPCandidates.size() == 1) {
      registerExtension(element, myEPCandidates.iterator().next(), extensionClass);
    }
    else {
      final BaseListPopupStep<ExtensionPointCandidate> popupStep =
        new BaseListPopupStep<>(DevKitBundle.message("register.extension.fix.popup.title"), new ArrayList<>(myEPCandidates)) {
          @Override
          public PopupStep onChosen(ExtensionPointCandidate selectedValue, boolean finalChoice) {
            registerExtension(element, selectedValue, extensionClass);
            return FINAL_CHOICE;
          }
        };
      JBPopupFactory.getInstance().createListPopup(popupStep).showInBestPositionFor(editor);
    }
  }

  private static void registerExtension(final DomFileElement<IdeaPlugin> selectedValue,
                                        final ExtensionPointCandidate candidate,
                                        final PsiClass extensionClass) {
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
        String qualifiedName = ClassUtil.getJVMClassName(extensionClass);
        if (candidate.attributeName != null) {
          tag.setAttribute(candidate.attributeName, qualifiedName);
        }
        else {
          XmlTag subTag = tag.createChildTag(candidate.tagName, null, qualifiedName, false);
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

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return IntentionPreviewInfo.EMPTY;
  }
}
