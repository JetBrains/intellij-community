/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.Consumer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PackageDotHtmlMayBePackageInfoInspection extends PackageDotHtmlMayBePackageInfoInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean packageInfoExists = ((Boolean)infos[1]).booleanValue();
    if (packageInfoExists) {
      return new DeletePackageDotHtmlFix();
    }
    final String aPackage = (String)infos[0];
    return new PackageDotHtmlMayBePackageInfoFix(aPackage);
  }

  private static class DeletePackageDotHtmlFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.delete.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof XmlFile)) {
        return;
      }
      final XmlFile xmlFile = (XmlFile)element;
      new WriteCommandAction.Simple(project, InspectionGadgetsBundle.message("package.dot.html.delete.command"), xmlFile) {
        @Override
        protected void run() throws Throwable {
          element.delete();
        }

        @Override
        protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
          return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
        }
      }.execute();
    }
  }

  private static class PackageDotHtmlMayBePackageInfoFix extends InspectionGadgetsFix {

    private final String aPackage;

    public PackageDotHtmlMayBePackageInfoFix(String aPackage) {
      this.aPackage = aPackage;
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.convert.quickfix");
    }

    @Override
    protected void doFix(final Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof XmlFile)) {
        return;
      }
      final XmlFile xmlFile = (XmlFile)element;
      final PsiDirectory directory = xmlFile.getContainingDirectory();
      if (directory == null) {
        return;
      }
      final PsiFile file = directory.findFile("package-info.java");
      if (file != null) {
        return;
      }
      new WriteCommandAction.Simple(project, InspectionGadgetsBundle.message("package.dot.html.convert.command"), file) {
        @Override
        protected void run() throws Throwable {
          final PsiJavaFile file = (PsiJavaFile)directory.createFile("package-info.java");
          CommandProcessor.getInstance().addAffectedFiles(project, file.getVirtualFile());
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
          String packageInfoText = getPackageInfoText(xmlFile);
          if (packageInfoText == null) {
            packageInfoText = xmlFile.getText();
          }
          final StringBuilder commentText = new StringBuilder("/**\n");
          final String[] lines = StringUtil.splitByLines(packageInfoText);
          boolean appended = false;
          for (String line : lines) {
            if (!appended && line.isEmpty()) {
              // skip empty lines at the beginning
              continue;
            }
            commentText.append(" * ").append(line).append('\n');
            appended = true;
          }
          commentText.append("*/");
          final PsiDocComment comment = elementFactory.createDocCommentFromText(commentText.toString());
          if (!aPackage.isEmpty()) {
            final PsiPackageStatement packageStatement = elementFactory.createPackageStatement(aPackage);
            final PsiElement addedElement = file.add(packageStatement);
            file.addBefore(comment, addedElement);
          }
          else {
            file.add(comment);
          }
          element.delete();
          if (!isOnTheFly()) {
            return;
          }
          final AsyncResult<DataContext> dataContextFromFocus = DataManager.getInstance().getDataContextFromFocus();
          dataContextFromFocus.doWhenDone(new Consumer<DataContext>() {
              @Override
              public void consume(DataContext dataContext) {
                final FileEditorManager editorManager = FileEditorManager.getInstance(project);
                final VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile == null) {
                  return;
                }
                editorManager.openFile(virtualFile, true);
              }
            }
          );
        }

        @Override
        protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
          return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
        }
      }.execute();
    }

    @Nullable
    private static String getPackageInfoText(XmlFile xmlFile) {
      final XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag == null) {
        return null;
      }
      final PsiElement[] children = rootTag.getChildren();
      for (PsiElement child : children) {
        if (!(child instanceof HtmlTag)) {
          continue;
        }
        final HtmlTag htmlTag = (HtmlTag)child;
        @NonNls final String name = htmlTag.getName();
        if ("body".equalsIgnoreCase(name)) {
          final XmlTagValue value = htmlTag.getValue();
          return value.getText();
        }
      }
      return null;
    }
  }
}
