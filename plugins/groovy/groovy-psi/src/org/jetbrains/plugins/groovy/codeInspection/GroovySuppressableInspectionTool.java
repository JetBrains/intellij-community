// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.BatchSuppressManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;

import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;

/**
 * @author peter
 */
public abstract class GroovySuppressableInspectionTool extends LocalInspectionTool {

  @NotNull
  public static SuppressQuickFix[] getSuppressActions(@NotNull String toolId) {
    if (GroovyUnusedDeclarationInspection.SHORT_NAME.equals(toolId)) {
      // substitute id for suppression
      toolId = UNUSED_SYMBOL_SHORT_NAME;
    }
    return new SuppressQuickFix[]{
      new SuppressByGroovyCommentFix(toolId),
      new SuppressForMemberFix(toolId, false),
      new SuppressForMemberFix(toolId, true),
    };
  }

  public static boolean isElementToolSuppressedIn(final PsiElement place, @NotNull String toolId) {
    return getElementToolSuppressedIn(place, toolId) != null;
  }

  @Nullable
  public static PsiElement getElementToolSuppressedIn(final PsiElement place, @NotNull String toolId) {
    if (place == null) return null;
    if (GroovyUnusedDeclarationInspection.SHORT_NAME.equals(toolId)) {
      PsiElement forUnused = getElementToolSuppressedIn(place, UNUSED_SYMBOL_SHORT_NAME);
      if (forUnused != null) {
        return forUnused;
      }
      else {
        // fallback to checking for old toolId to avoid introducing warnings into old code
      }
    }

    return ReadAction.compute(()->{
      final PsiElement statement = PsiUtil.findEnclosingStatement(place);
      if (statement != null) {
        PsiElement prev = statement.getPrevSibling();
        while (prev != null && StringUtil.isEmpty(prev.getText().trim())) {
          prev = prev.getPrevSibling();
        }
        if (prev instanceof PsiComment) {
          String text = prev.getText();
          Matcher matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
          if (matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)) {
            return prev;
          }
        }
      }

      GrMember member = null;
      GrDocComment docComment = PsiTreeUtil.getParentOfType(place, GrDocComment.class);
      if (docComment != null) {
        GrDocCommentOwner owner = docComment.getOwner();
        if (owner instanceof GrMember) {
          member = (GrMember)owner;
        }
      }
      if (member == null) {
        member = PsiTreeUtil.getNonStrictParentOfType(place, GrMember.class);
      }

      while (member != null) {
        GrModifierList modifierList = member.getModifierList();
        for (String ids : getInspectionIdsSuppressedInAnnotation(modifierList)) {
          if (SuppressionUtil.isInspectionToolIdMentioned(ids, toolId)) {
            return modifierList;
          }
        }

        member = PsiTreeUtil.getParentOfType(member, GrMember.class);
      }

      return null;
    });
  }

  @NotNull
  private static Collection<String> getInspectionIdsSuppressedInAnnotation(final GrModifierList modifierList) {
    if (modifierList == null) {
      return Collections.emptyList();
    }
    PsiAnnotation annotation = modifierList.findAnnotation(BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    final GrAnnotationMemberValue attributeValue = (GrAnnotationMemberValue)annotation.findAttributeValue(null);
    Collection<String> result = new ArrayList<>();
    if (attributeValue instanceof GrAnnotationArrayInitializer) {
      for (GrAnnotationMemberValue annotationMemberValue : ((GrAnnotationArrayInitializer)attributeValue).getInitializers()) {
        final String id = getInspectionIdSuppressedInAnnotationAttribute(annotationMemberValue);
        if (id != null) {
          result.add(id);
        }
      }
    }
    else {
      final String id = getInspectionIdSuppressedInAnnotationAttribute(attributeValue);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  @Nullable
  private static String getInspectionIdSuppressedInAnnotationAttribute(GrAnnotationMemberValue element) {
    if (element instanceof GrLiteral) {
      final Object value = ((GrLiteral)element).getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    return null;
  }



}
