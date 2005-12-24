/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.ChooseModulesDialog;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.annotations.NonNls;

import java.util.List;

abstract class AbstractRegisterFix implements LocalQuickFix, DescriptorUtil.Patcher {
  protected final PsiClass myClass;

  public AbstractRegisterFix(PsiClass klass) {
    myClass = klass;
  }

  public String getFamilyName() {
    return DevKitBundle.message("inspections.component.not.registered.quickfix.family");
  }

  public String getName() {
    return DevKitBundle.message("inspections.component.not.registered.quickfix.name", getType());
  }

  protected abstract String getType();

  // copy of com.intellij.ide.actions.CreateElementActionBase.filterMessage()
  protected static String filterMessage(String message) {
    if (message == null) return null;
    @NonNls final String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)) {
      message = message.substring(ioExceptionPrefix.length());
    }
    return message;
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    final Module module = VfsUtil.getModuleForFile(project, myClass.getContainingFile().getVirtualFile());

    try {
      if (PluginModuleType.isOfType(module)) {
        final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
        if (pluginXml != null) {
          DescriptorUtil.patchPluginXml(this, myClass, pluginXml);
        }
      } else {
        List<Module> modules = PluginModuleType.getCandidateModules(module);
        if (modules.size() > 1) {
          final ChooseModulesDialog dialog = new ChooseModulesDialog(project, modules, getName());
          dialog.show();

          if (!dialog.isOK()) {
            return;
          }
          modules = dialog.getSelectedModules();
        }
        final XmlFile[] pluginXmls = new XmlFile[modules.size()];
        for (int i = 0; i < pluginXmls.length; i++) {
          pluginXmls[i] = PluginModuleType.getPluginXml(modules.get(i));
        }

        DescriptorUtil.patchPluginXml(this, myClass, pluginXmls);
      }
    }
    catch (IncorrectOperationException e) {
      Messages.showMessageDialog(project,
              filterMessage(e.getMessage()),
              DevKitBundle.message("inspections.component.not.registered.quickfix.error", getType()),
              Messages.getErrorIcon());
    }
  }
}
