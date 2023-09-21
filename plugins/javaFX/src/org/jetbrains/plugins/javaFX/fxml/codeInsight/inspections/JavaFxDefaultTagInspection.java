// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;

public final class JavaFxDefaultTagInspection extends XmlSuppressableInspectionTool{
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        super.visitXmlTag(tag);
        final XmlElementDescriptor descriptor = tag.getDescriptor();
        if (descriptor instanceof JavaFxPropertyTagDescriptor) {
          final PsiClass parentTagClass = JavaFxPsiUtil.getTagClass(tag.getParentTag());
          if (parentTagClass != null) {
            final String propertyName = JavaFxPsiUtil.getDefaultPropertyName(parentTagClass);
            final String tagName = tag.getName();
            if (Comparing.strEqual(tagName, propertyName) && !isCollectionAssignment(parentTagClass, propertyName, tag)) {
              final TextRange startTagRange = XmlTagUtil.getStartTagRange(tag);
              final TextRange rangeInElement = startTagRange != null ? startTagRange.shiftRight(-tag.getTextOffset()) : null;
              holder.registerProblem(tag, rangeInElement, JavaFXBundle.message("inspection.javafx.default.tag.could.be.removed"), new UnwrapTagFix(tagName));
            }
          }
        }
      }
    };
  }

  private static boolean isCollectionAssignment(@NotNull PsiClass parentTagClass, @NotNull String propertyName, @NotNull XmlTag tag) {
    final XmlTag[] subTags = tag.getSubTags();
    if (subTags.length != 0) {
      final PsiClass tagValueClass = JavaFxPsiUtil.getTagValueClass(subTags[subTags.length - 1]);
      if (JavaFxPsiUtil.isObservableCollection(tagValueClass)) {
        final PsiMember property = JavaFxPsiUtil.getWritableProperties(parentTagClass).get(propertyName);
        if (property != null) {
          final PsiType propertyType = JavaFxPsiUtil.getWritablePropertyType(parentTagClass, property);
          final PsiClass propertyClass = PsiUtil.resolveClassInClassTypeOnly(propertyType);
          if (JavaFxPsiUtil.isObservableCollection(propertyClass)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
