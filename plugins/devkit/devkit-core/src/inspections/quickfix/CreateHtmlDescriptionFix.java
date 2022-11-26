// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DescriptionCheckerUtil;
import org.jetbrains.idea.devkit.inspections.DescriptionType;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
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
      return StreamEx.of(dirs).map(PsiDirectory::getParentDirectory).nonNull().map(PsiDirectory::getVirtualFile).toList();
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

  @Override
  @NotNull
  public String getName() {
    return DevKitBundle.message("create.description.file", getNewFileName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return DevKitBundle.message("create.description.file.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiDirectory[] dirs = getDirectories();
    final List<VirtualFile> roots = getPotentialRoots(myModule, dirs);
    if (roots.size() == 1) {
      ApplicationManager.getApplication().runWriteAction(() -> createDescription(roots.get(0)));
    }
    else {
      final Editor editor = FileEditorManager.getInstance(myModule.getProject()).getSelectedTextEditor();
      if (editor == null) return;
      JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(roots)
                    .setRenderer(SimpleListCellRenderer.create("", VirtualFile::getPath))
                    .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                    .setTitle(DevKitBundle.message("select.target.location.of.description", myFilename))
                    .setItemChosenCallback((root) -> ApplicationManager.getApplication().runWriteAction(() -> createDescription(root)))
                    .createPopup()
                    .showInBestPositionFor(editor);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
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
    if (psiRoot == null) return;
    PsiDirectory descrRoot =
      StreamEx.of(psiRoot.getSubdirectories()).findFirst(dir -> getDescriptionFolderName().equals(dir.getName())).orElse(null);

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

  @Override
  public Icon getIcon(int flags) {
    return new LayeredIcon(AllIcons.FileTypes.Html, AllIcons.Actions.New);
  }

  private VirtualFile[] prepare(VirtualFile[] roots) {
    VirtualFile[] found = Arrays.stream(roots).filter(this::containsDescriptionDir).toArray(VirtualFile[]::new);
    return found.length > 0 ? found : roots;
  }

  private boolean containsDescriptionDir(VirtualFile root) {
    if (!root.isDirectory()) return false;
    return ContainerUtil.exists(root.getChildren(), file -> file.isDirectory() && getDescriptionFolderName().equals(file.getName()));
  }

  private String getDescriptionFolderName() {
    return myDescriptionType.getDescriptionFolder();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return IntentionPreviewInfo.EMPTY;
  }
}
