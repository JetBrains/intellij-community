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

package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;

/**
 * @author peter
 */
public abstract class GroovySuppressableInspectionTool extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  @Nullable
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    final HighlightDisplayKey displayKey = HighlightDisplayKey.find(getShortName());
    return new SuppressIntentionAction[]{
      new SuppressByGroovyCommentFix(displayKey),
      new SuppressForMemberFix(displayKey, false),
      new SuppressForMemberFix(displayKey, true),
    };

  }

  public boolean isSuppressedFor(final PsiElement element) {
    return getElementToolSuppressedIn(element, getID()) != null;
  }

  @Nullable
  private static PsiElement getElementToolSuppressedIn(final PsiElement place, final String toolId) {
    if (place == null) return null;
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      @Nullable
      public PsiElement compute() {
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

        GrMember member = PsiTreeUtil.getNonStrictParentOfType(place, GrMember.class);
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
      }
    });
  }

  @NotNull
  private static Collection<String> getInspectionIdsSuppressedInAnnotation(final GrModifierList modifierList) {
    if (modifierList == null) {
      return Collections.emptyList();
    }
    PsiAnnotation annotation = modifierList.findAnnotation(SuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    final GrAnnotationMemberValue attributeValue = (GrAnnotationMemberValue)annotation.findAttributeValue(null);
    Collection<String> result = new ArrayList<String>();
    if (attributeValue instanceof GrListOrMap) {
      for (GrExpression annotationMemberValue : ((GrListOrMap)attributeValue).getInitializers()) {
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
