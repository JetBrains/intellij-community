// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;
import java.nio.file.FileSystems;

/**
 * @author Max Medvedev
 */
public class GrMoveToDirFix extends GroovyFix {
  private final @NlsSafe String myPackageName;

  public GrMoveToDirFix(String packageName) {
    myPackageName = packageName;
  }

  @Override
  public @NotNull String getName() {
    String packName = StringUtil.isEmptyOrSpaces(myPackageName) ? "default package" : myPackageName;
    return GroovyBundle.message("move.to.correct.dir", packName);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiFile file = previewDescriptor.getPsiElement().getContainingFile();

    Icon fileIcon = JetgroovyIcons.Groovy.GroovyFile;
    Icon dirIcon = AllIcons.Nodes.Folder;
    HtmlBuilder builder = new HtmlBuilder()
      .append(HtmlChunk.icon("file", fileIcon))
      .nbsp()
      .append(file.getName())
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(HtmlChunk.icon("dir", dirIcon))
      .nbsp()
      .append(myPackageName.replace(".", FileSystems.getDefault().getSeparator())); //NON-NLS
    return new IntentionPreviewInfo.Html(builder.wrapWith("p"));
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("move.to.correct.dir.family.name");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    PsiFile file = descriptor.getPsiElement().getContainingFile();

    if (!(file instanceof GroovyFile)) return;

    VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) return;

    final Module module = ModuleUtilCore.findModuleForFile(vfile, project);
    if (module == null) return;

    final String packageName = ((GroovyFile)file).getPackageName();
    PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, null, true);
    if (directory == null) return;

    String error = RefactoringMessageUtil.checkCanCreateFile(directory, file.getName());
    if (error != null) {
      Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }
    new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{file}, directory, false, false, false, null, null).run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
