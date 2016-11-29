/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;

/**
 * User: anna
 */
public class JavaFxDefaultTagInspection extends XmlSuppressableInspectionTool{
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
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
              holder.registerProblem(tag, rangeInElement, "Default property tag could be removed", new UnwrapTagFix(tagName));
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
        final PsiMember property = JavaFxPsiUtil.collectWritableProperties(parentTagClass).get(propertyName);
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
