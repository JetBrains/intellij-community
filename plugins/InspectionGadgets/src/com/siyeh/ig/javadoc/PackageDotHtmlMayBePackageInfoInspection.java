/*
 * Copyright 2011-2017 Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PackageDotHtmlMayBePackageInfoInspection extends BaseInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean packageInfoExists = ((Boolean)infos[1]).booleanValue();
    if (packageInfoExists) {
      return new DeletePackageDotHtmlFix();
    }
    final String aPackage = (String)infos[0];
    return new PackageDotHtmlMayBePackageInfoFix(aPackage);
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (((Boolean)infos[1]).booleanValue()) {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.exists.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageDotHtmlMayBePackageInfoVisitor();
  }

  private static class DeletePackageDotHtmlFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.delete.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof XmlFile)) {
        return;
      }
      element.delete();
    }
  }

  private static class PackageDotHtmlMayBePackageInfoFix extends InspectionGadgetsFix {

    private final String aPackage;

    PackageDotHtmlMayBePackageInfoFix(String aPackage) {
      this.aPackage = aPackage;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.convert.quickfix");
    }

    @Override
    protected void doFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof XmlFile)) {
        return;
      }
      final XmlFile xmlFile = (XmlFile)element;
      final PsiDirectory directory = xmlFile.getContainingDirectory();
      if (directory == null) {
        return;
      }
      final PsiFile file = directory.findFile(PsiPackage.PACKAGE_INFO_FILE);
      if (file != null) {
        return;
      }
      final String packageInfoText = getPackageInfoText(xmlFile);
      final PsiJavaFile packageInfoFile = (PsiJavaFile)directory.createFile(PsiPackage.PACKAGE_INFO_FILE);
      final String commentText = buildCommentText(packageInfoText);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final PsiDocComment comment = elementFactory.createDocCommentFromText(commentText);
      if (!aPackage.isEmpty()) {
        final PsiPackageStatement packageStatement = elementFactory.createPackageStatement(aPackage);
        final PsiElement addedElement = packageInfoFile.add(packageStatement);
        packageInfoFile.addBefore(comment, addedElement);
      }
      else {
        packageInfoFile.add(comment);
      }
      xmlFile.delete();
      if (isOnTheFly()) {
        packageInfoFile.navigate(true);
      }
    }

    @NotNull
    private static String buildCommentText(String packageInfoText) {
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
      return commentText.toString();
    }

    @NotNull
    static String getPackageInfoText(XmlFile xmlFile) {
      final XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null) {
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
      }
      return xmlFile.getText();
    }
  }

  private static class PackageDotHtmlMayBePackageInfoVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(@NotNull PsiFile file) {
      super.visitFile(file);
      if (!(file instanceof XmlFile)) {
        return;
      }
      @NonNls final String fileName = file.getName();
      if (!"package.html".equals(fileName)) {
        return;
      }
      final PsiDirectory directory = file.getContainingDirectory();
      if (directory == null) {
        return;
      }
      final String aPackage = getPackage(directory);
      if (aPackage == null) {
        return;
      }
      final boolean exists = directory.findFile("package-info.java") != null;
      registerError(file, aPackage, Boolean.valueOf(exists));
    }

    public static String getPackage(@NotNull PsiDirectory directory) {
      final VirtualFile virtualFile = directory.getVirtualFile();
      final Project project = directory.getProject();
      return PackageIndex.getInstance(project).getPackageNameByDirectory(virtualFile);
    }
  }
}
