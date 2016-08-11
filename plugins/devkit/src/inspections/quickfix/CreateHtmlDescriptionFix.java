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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DescriptionCheckerUtil;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class CreateHtmlDescriptionFix implements LocalQuickFix, Iconable {

  @NonNls
  private static final String TEMPLATE_NAME = "InspectionDescription.html";

  private final String myFilename;
  private final Module myModule;
  private final DescriptionType myDescriptionType;

  public CreateHtmlDescriptionFix(String filename, Module module, DescriptionType descriptionType) {
    myModule = module;
    myDescriptionType = descriptionType;
    myFilename = getNormalizedFileName(filename);
  }

  private boolean isFixedDescriptionFilename() {
    return myDescriptionType.isFixedDescriptionFilename();
  }

  private static List<VirtualFile> getPotentialRoots(Module module, PsiDirectory[] dirs) {
    if (dirs.length != 0) {
      final List<VirtualFile> result = new ArrayList<>();
      for (PsiDirectory dir : dirs) {
        final PsiDirectory parent = dir.getParentDirectory();
        if (parent != null) result.add(parent.getVirtualFile());
      }
      return result;
    }
    else {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      List<VirtualFile> resourceRoots = rootManager.getSourceRoots(JavaResourceRootType.RESOURCE);
      if (!resourceRoots.isEmpty()) {
        return resourceRoots;
      }
      return rootManager.getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
    }
  }

  private String getNormalizedFileName(String filename) {
    return myDescriptionType.isFixedDescriptionFilename() ? filename : filename + ".html";
  }

  @NotNull
  public String getName() {
    return DevKitBundle.message("create.description.file");
  }

  @NotNull
  public String getFamilyName() {
    return "DevKit";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiDirectory[] dirs = getDirectories();
    final List<VirtualFile> virtualFiles = getPotentialRoots(myModule, dirs);
    final VirtualFile[] roots = prepare(VfsUtilCore.toVirtualFileArray(virtualFiles));
    if (roots.length == 1) {
      ApplicationManager.getApplication().runWriteAction(() -> createDescription(roots[0]));
    }
    else {
      List<String> options = new ArrayList<>();
      for (VirtualFile file : roots) {
        String path = getPath(file);
        options.add(path);
      }
      final JBList files = new JBList(ArrayUtil.toStringArray(options));
      files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      final JBPopup popup = JBPopupFactory.getInstance()
        .createListPopupBuilder(files)
        .setTitle(DevKitBundle.message("select.target.location.of.description", myFilename))
        .setItemChoosenCallback(() -> {
          final int index = files.getSelectedIndex();
          if (0 <= index && index < roots.length) {
            ApplicationManager.getApplication().runWriteAction(() -> createDescription(roots[index]));
          }
        }).createPopup();
      final Editor editor = FileEditorManager.getInstance(myModule.getProject()).getSelectedTextEditor();
      if (editor == null) return;
      popup.showInBestPositionFor(editor);
    }
  }

  private String getPath(VirtualFile file) {
    String path = file.getPresentableUrl() + File.separator + getDescriptionFolderName() + File.separator + myFilename;
    if (isFixedDescriptionFilename()) {
      path += File.separator + "description.html";
    }
    return path;
  }

  private PsiDirectory[] getDirectories() {
    return DescriptionCheckerUtil.getDescriptionsDirs(myModule, myDescriptionType);
  }

  private void createDescription(VirtualFile root) {
    if (!root.isDirectory()) return;
    final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
    final PsiDirectory psiRoot = psiManager.findDirectory(root);
    PsiDirectory descrRoot = null;
    if (psiRoot == null) return;
    for (PsiDirectory dir : psiRoot.getSubdirectories()) {
      if (getDescriptionFolderName().equals(dir.getName())) {
        descrRoot = dir;
        break;
      }
    }

    try {
      descrRoot = descrRoot == null ? psiRoot.createSubdirectory(getDescriptionFolderName()) : descrRoot;
      if (isFixedDescriptionFilename()) {
        PsiDirectory dir = descrRoot.findSubdirectory(myFilename);
        if (dir == null) {
          descrRoot = descrRoot.createSubdirectory(myFilename);
        }
      }
      final FileTemplate descrTemplate = FileTemplateManager.getInstance(myModule.getProject()).getJ2eeTemplate(TEMPLATE_NAME);
      final PsiElement template =
        FileTemplateUtil.createFromTemplate(descrTemplate, getNewFileName(), null, descrRoot);
      if (template instanceof PsiFile) {
        final VirtualFile file = ((PsiFile)template).getVirtualFile();
        if (file != null) {
          FileEditorManager.getInstance(myModule.getProject()).openFile(file, true);
        }
      }
    }
    catch (Exception e) {//
    }
  }

  private String getNewFileName() {
    return isFixedDescriptionFilename() ? "description.html" : myFilename;
  }

  public Icon getIcon(int flags) {
    return new LayeredIcon(AllIcons.FileTypes.Html, AllIcons.Actions.New);
  }

  private VirtualFile[] prepare(VirtualFile[] roots) {
    List<VirtualFile> found = new ArrayList<>();
    for (VirtualFile root : roots) {
      if (containsDescriptionDir(root)) {
        found.add(root);
      }
    }
    return found.size() > 0 ? VfsUtilCore.toVirtualFileArray(found) : roots;
  }

  private boolean containsDescriptionDir(VirtualFile root) {
    if (!root.isDirectory()) return false;
    for (VirtualFile file : root.getChildren()) {
      if (file.isDirectory() && getDescriptionFolderName().equals(file.getName())) {
        return true;
      }
    }
    return false;
  }

  private String getDescriptionFolderName() {
    return myDescriptionType.getDescriptionFolder();
  }
}
