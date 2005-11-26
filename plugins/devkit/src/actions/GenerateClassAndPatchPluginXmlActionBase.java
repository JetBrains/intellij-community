/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.jetbrains.idea.devkit.actions;

import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.build.PluginModuleBuildProperties;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public abstract class GenerateClassAndPatchPluginXmlActionBase extends CreateElementActionBase {
  public GenerateClassAndPatchPluginXmlActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected abstract void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException;
  protected abstract String getClassNamePrompt();
  protected abstract String getClassNamePromptTitle();

  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getClassNamePrompt(), getClassNamePromptTitle(), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    if (getPluginXml(getModule(directory)) == null) throw new IncorrectOperationException("plugin.xml descriptor cannot be found");
    directory.checkCreateClass(newName);
  }

  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    final PsiClass klass = directory.createClass(newName, getClassTemplateName());
    patchPluginXml(getPluginXml(getModule(directory)), klass);
    return new PsiElement[] {klass};
  }

  protected abstract String getClassTemplateName();


  private Module getModule(PsiDirectory dir) {
    Project project = dir.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    final VirtualFile vFile = dir.getVirtualFile();
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      final OrderEntry[] orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.length == 0) {
        return null;
      }
      Set<Module> modules = new HashSet<Module>();
      for (OrderEntry orderEntry : orderEntries) {
        modules.add(orderEntry.getOwnerModule());
      }
      final Module[] candidates = modules.toArray(new Module[modules.size()]);
      Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
      return candidates[0];
    }
    return fileIndex.getModuleForFile(vFile);
  }

  private XmlFile getPluginXml(Module module) {
    if (module == null) return null;
    if (module.getModuleType() != PluginModuleType.getInstance()) return null;

    final ModuleBuildProperties buildProperties = module.getComponent(ModuleBuildProperties.class);
    if (!(buildProperties instanceof PluginModuleBuildProperties)) return null;
    final VirtualFilePointer pluginXMLPointer = ((PluginModuleBuildProperties)buildProperties).getPluginXMLPointer();
    final VirtualFile vFile = pluginXMLPointer.getFile();
    if (vFile == null) return null;
    final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(vFile);
    return file instanceof XmlFile ? (XmlFile)file : null;
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      final DataContext context = e.getDataContext();
      Module module = (Module)context.getData(DataConstants.MODULE);
      if (module == null || getPluginXml(module) == null) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
      }
    }
  }
}
