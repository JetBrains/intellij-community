// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

@ApiStatus.Internal
public abstract class AbstractRegisterFix implements LocalQuickFix, DescriptorUtil.Patcher {
  protected final SmartPsiElementPointer<PsiClass> myPointer;
  protected static final Logger LOG = Logger.getInstance(AbstractRegisterFix.class);

  protected AbstractRegisterFix(@NotNull PsiClass psiClass) {
    myPointer = SmartPointerManager.createPointer(psiClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return DevKitBundle.message("inspections.component.not.registered.quickfix.family", StringUtil.toLowerCase(getType()));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getName() {
    return DevKitBundle.message("inspections.component.not.registered.quickfix.name", getType());
  }

  protected abstract String getType();

  // copy of com.intellij.ide.actions.CreateElementActionBase.filterMessage()
  protected static String filterMessage(String message) {
    if (message == null) return null;
    @NonNls String ioExceptionPrefix = "java.io.IOException:";
    message = StringUtil.trimStart(message, ioExceptionPrefix);
    return message;
  }

  @Override
  public void applyFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) return;
    PsiFile psiFile = myPointer.getContainingFile();
    final PsiClass element = myPointer.getElement();
    if (element == null) {
      LOG.info("Element is null for PsiPointer: " + myPointer);
      return;
    }
    LOG.assertTrue(psiFile != null);
    final Module module = ModuleUtilCore.findModuleForFile(psiFile.getVirtualFile(), project);
    assert module != null;

    Runnable command = () -> {
      try {
        XmlFile pluginXml = PluginModuleType.getPluginXml(module);
        if (pluginXml == null) {
          pluginXml = DevkitActionsUtil.choosePluginModuleDescriptor(psiFile.getContainingDirectory());
        }

        if (pluginXml != null) {
          DescriptorUtil.patchPluginXml(this, element, pluginXml);
        }
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
      } catch (IncorrectOperationException e) {
        Messages.showMessageDialog(project, filterMessage(e.getMessage()),
                                   DevKitBundle.message("inspections.component.not.registered.quickfix.error", getType()),
                                   Messages.getErrorIcon());
      }
    };

    CommandProcessor.getInstance().executeCommand(project, command, getName(), null);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return IntentionPreviewInfo.EMPTY;
  }
}
