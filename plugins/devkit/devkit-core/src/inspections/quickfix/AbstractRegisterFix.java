/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
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
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.actions.DevkitActionsUtil;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

abstract class AbstractRegisterFix implements LocalQuickFix, DescriptorUtil.Patcher {
  protected final SmartPsiElementPointer<PsiClass> myPointer;
  protected static final Logger LOG = Logger.getInstance(AbstractRegisterFix.class);

  protected AbstractRegisterFix(@NotNull SmartPsiElementPointer<PsiClass> klass) {
    myPointer = klass;
  }

  @NotNull
  public String getFamilyName() {
    return DevKitBundle.message("inspections.component.not.registered.quickfix.family", StringUtil.toLowerCase(getType()));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  public String getName() {
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

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
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
}
