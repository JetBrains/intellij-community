/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.InspectionDescriptionNotFoundInspection;
import org.jetbrains.idea.devkit.inspections.IntentionDescriptionNotFoundInspection;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class CreateHtmlDescriptionFix implements LocalQuickFix, Iconable {
  private final String myFilename;
  private final Module myModule;  
  @NonNls private static final String TEMPLATE_NAME = "InspectionDescription.html";
  private static final Icon NEW_HTML_ICON = IconLoader.getIcon("/new_html.png");
  private final boolean isIntention;

  public CreateHtmlDescriptionFix(String filename, Module module, boolean isIntention) {
    myModule = module;
    this.isIntention = isIntention;
    myFilename = isIntention ? filename : filename + ".html";
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
    final List<VirtualFile> virtualFiles = isIntention ?
                                           IntentionDescriptionNotFoundInspection.getPotentialRoots(myModule)
                                           :
                                           InspectionDescriptionNotFoundInspection.getPotentialRoots(myModule);
    final VirtualFile[] roots = prepare(VfsUtil.toVirtualFileArray(virtualFiles));
    if (roots.length == 1) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          createDescription(roots[0]);
        }
      });

    }
    else {
      List<String> options = new ArrayList<String>();
      for (VirtualFile file : roots) {
        String path = file.getPresentableUrl() + File.separator + getDescriptionFolderName() + File.separator + myFilename;
        if (isIntention) {
          path += File.separator + "description.html";
        }
        options.add(path);
      }
      final JBList files = new JBList(ArrayUtil.toStringArray(options));
      files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      final JBPopup popup = JBPopupFactory.getInstance()
        .createListPopupBuilder(files)
        .setTitle(DevKitBundle.message("select.target.location.of.description", myFilename))
        .setItemChoosenCallback(new Runnable() {
        public void run() {
          final int index = files.getSelectedIndex();
          if (0 <= index && index < roots.length) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                createDescription(roots[index]);
              }
            });
          }
        }
      }).createPopup();
      final Editor editor = FileEditorManager.getInstance(myModule.getProject()).getSelectedTextEditor();
      if (editor == null) return;
      popup.showInBestPositionFor(editor);
    }
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
      if (isIntention) {
        PsiDirectory dir = descrRoot.findSubdirectory(myFilename);
        if (dir == null) {
          descrRoot = descrRoot.createSubdirectory(myFilename);
        }
      }
      final FileTemplate descrTemplate = FileTemplateManager.getInstance().getJ2eeTemplate(TEMPLATE_NAME);
      final PsiElement template = FileTemplateUtil.createFromTemplate(descrTemplate, isIntention? "description.html" : myFilename, null, descrRoot);
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

  public Icon getIcon(int flags) {
    return NEW_HTML_ICON;
  }

  private VirtualFile[] prepare(VirtualFile[] roots) {
    List<VirtualFile> found = new ArrayList<VirtualFile>();
    for (VirtualFile root : roots) {
      if (containsDescriptionDir(root)) {
        found.add(root);
      }
    }
    return found.size() > 0 ? VfsUtil.toVirtualFileArray(found) : roots;
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
    return isIntention ? "intentionDescriptions" : "inspectionDescriptions";
  }
}
