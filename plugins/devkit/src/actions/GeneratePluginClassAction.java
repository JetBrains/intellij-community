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
package org.jetbrains.idea.devkit.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ChooseModulesDialog;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public abstract class GeneratePluginClassAction extends CreateElementActionBase implements DescriptorUtil.Patcher {
  protected final Set<XmlFile> myFilesToPatch = new HashSet<>();

  // length == 1 is important to make MyInputValidator close the dialog when
  // module selection is canceled. That's some weird interface actually...
  private static final PsiElement[] CANCELED = new PsiElement[1];

  public GeneratePluginClassAction(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @NotNull protected final PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    try {
      final PsiElement[] psiElements = invokeDialogImpl(project, directory);
      return psiElements == CANCELED ? PsiElement.EMPTY_ARRAY : psiElements;
    } finally {
      myFilesToPatch.clear();
    }
  }

  protected abstract PsiElement[] invokeDialogImpl(Project project, PsiDirectory directory);

  private void addPluginModule(Module module) {
    final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml != null) myFilesToPatch.add(pluginXml);
  }

  public void update(final AnActionEvent e) {
    super.update(e);

    final Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      final DataContext context = e.getDataContext();
      final Module module = e.getData(LangDataKeys.MODULE);
      if (PluginModuleType.isPluginModuleOrDependency(module)) {
        final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
        final Project project = e.getProject();
        if (view != null && project != null) {
          // from com.intellij.ide.actions.CreateClassAction.update()
          ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          PsiDirectory[] dirs = view.getDirectories();
          for (PsiDirectory dir : dirs) {
            if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) &&
                JavaDirectoryService.getInstance().getPackage(dir) != null) {
              return;
            }
          }
        }
      }

      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  @Nullable
  protected static Module getModule(PsiDirectory dir) {
    Project project = dir.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    final VirtualFile vFile = dir.getVirtualFile();
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.isEmpty()) {
        return null;
      }
      Set<Module> modules = new HashSet<>();
      for (OrderEntry orderEntry : orderEntries) {
        modules.add(orderEntry.getOwnerModule());
      }
      final Module[] candidates = modules.toArray(new Module[modules.size()]);
      Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
      return candidates[0];
    }
    return fileIndex.getModuleForFile(vFile);
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    final Project project = directory.getProject();
    final Module module = getModule(directory);

    if (module != null) {
      if (ModuleType.get(module) == PluginModuleType.getInstance()) {
        addPluginModule(module);
      }
      else {
        final List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
        final Iterator<Module> it = candidateModules.iterator();
        while (it.hasNext()) {
          Module m = it.next();
          if (PluginModuleType.getPluginXml(m) == null) it.remove();
        }

        if (candidateModules.size() == 1) {
          addPluginModule(candidateModules.get(0));
        }
        else {
          final ChooseModulesDialog dialog = new ChooseModulesDialog(project, candidateModules, getTemplatePresentation().getDescription());
          if (!dialog.showAndGet()) {
            // create() should return CANCELED now
            return CANCELED;
          }
          else {
            final List<Module> modules = dialog.getSelectedModules();
            for (Module m : modules) {
              addPluginModule(m);
            }
          }
        }
      }
    }

    if (myFilesToPatch.size() == 0) {
      throw new IncorrectOperationException(DevKitBundle.message("error.no.plugin.xml"));
    }
    if (myFilesToPatch.size() == 0) {
      // user canceled module selection
      return CANCELED;
    }

    final PsiClass klass = JavaDirectoryService.getInstance().createClass(directory, newName, getClassTemplateName());

    DescriptorUtil.patchPluginXml(this, klass, myFilesToPatch.toArray(new XmlFile[myFilesToPatch.size()]));

    return new PsiElement[] {klass};
  }

  @NonNls protected abstract String getClassTemplateName();
}
