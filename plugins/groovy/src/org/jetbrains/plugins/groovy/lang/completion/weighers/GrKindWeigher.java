/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.weighers;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.completion.GrMainCompletionProvider;
import org.jetbrains.plugins.groovy.lang.completion.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrKindWeigher extends CompletionWeigher {
  private static final Set<String> TRASH_CLASSES = new HashSet<>(10);
  private static final Set<String> PRIORITY_KEYWORDS = ContainerUtil.newHashSet(
    PsiKeyword.RETURN, PsiKeyword.INSTANCEOF, "in",
    PsiKeyword.PRIVATE, PsiKeyword.PROTECTED, PsiKeyword.PUBLIC, PsiKeyword.STATIC, "def",
    PsiKeyword.TRUE,  PsiKeyword.FALSE, PsiKeyword.NULL);

  static {
    TRASH_CLASSES.add(CommonClassNames.JAVA_LANG_CLASS);
    TRASH_CLASSES.add(CommonClassNames.JAVA_LANG_OBJECT);
    TRASH_CLASSES.add(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT);
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    final PsiElement position = location.getCompletionParameters().getPosition();
    if (!(position.getContainingFile() instanceof GroovyFileBase)) return null;

    Object o = element.getObject();
    if (o instanceof ResolveResult) {
      o = ((ResolveResult)o).getElement();
    }

    final PsiElement parent = position.getParent();
    final PsiElement qualifier = parent instanceof GrReferenceElement ? ((GrReferenceElement)parent).getQualifier() : null;
    if (qualifier == null) {
      if (o instanceof NamedArgumentDescriptor) {
        switch (((NamedArgumentDescriptor)o).getPriority()) {
          case ALWAYS_ON_TOP:
            return NotQualifiedKind.onTop;
          case AS_LOCAL_VARIABLE:
            return NotQualifiedKind.local;
          default:
            return NotQualifiedKind.unknown;
        }
      }
      if (o instanceof PsiVariable && !(o instanceof PsiField)) {
        return NotQualifiedKind.local;
      }

      PsiTypeLookupItem item = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
      if (item != null && item.getBracketsCount() > 0) {
        return NotQualifiedKind.arrayType;
      }

      if (isPriorityKeyword(o)) return NotQualifiedKind.local;
      if (isLightElement(o)) return NotQualifiedKind.unknown;
      if (o instanceof PsiClass) {
        if (((PsiClass)o).isAnnotationType() && GrMainCompletionProvider.AFTER_AT.accepts(position)) {
          final GrAnnotation annotation = PsiTreeUtil.getParentOfType(position, GrAnnotation.class);
          if (annotation != null) {
            PsiElement annoParent = annotation.getParent();
            PsiElement ownerToUse = annoParent instanceof PsiModifierList ? annoParent.getParent() : annoParent;
            PsiAnnotation.TargetType[] elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
            if (AnnotationTargetUtil.findAnnotationTarget((PsiClass)o, elementTypeFields) != null) {
              return NotQualifiedKind.restrictedClass;
            }
          }
        }
        if (GrMainCompletionProvider.IN_CATCH_TYPE.accepts(position) &&
            InheritanceUtil.isInheritor((PsiClass)o, CommonClassNames.JAVA_LANG_THROWABLE)) {
          return NotQualifiedKind.restrictedClass;
        }
      }
      if (o instanceof PsiMember) {
        final PsiClass containingClass = ((PsiMember)o).getContainingClass();
        if (isAccessor((PsiMember)o)) return NotQualifiedKind.accessor;
        if (o instanceof PsiClass && ((PsiClass)o).getContainingClass() == null || o instanceof PsiPackage) return NotQualifiedKind.unknown;
        if (o instanceof PsiClass) return NotQualifiedKind.innerClass;
        if (PsiTreeUtil.isContextAncestor(containingClass, position, false)) return NotQualifiedKind.currentClassMember;
        return NotQualifiedKind.member;
      }
      return NotQualifiedKind.unknown;
    }
    else {
      if (o instanceof PsiEnumConstant) return QualifiedKind.enumConstant;

      if (isLightElement(o)) return QualifiedKind.unknown;
      if (o instanceof PsiMember) {
        if (isTrashMethod((PsiMember)o)) return QualifiedKind.unknown;
        if (isAccessor((PsiMember)o)) return QualifiedKind.accessor;
        if (isQualifierClassMember((PsiMember)o, qualifier)) {
          return QualifiedKind.currentClassMember;
        }
        if (o instanceof PsiClass && ((PsiClass)o).getContainingClass() == null || o instanceof PsiPackage) return QualifiedKind.unknown;
        if (o instanceof PsiClass) return QualifiedKind.innerClass;
        return QualifiedKind.member;
      }
      return QualifiedKind.unknown;
    }
  }

  private static boolean isPriorityKeyword(Object o) {
    //noinspection SuspiciousMethodCalls
    return PRIORITY_KEYWORDS.contains(o);
  }

  private static boolean isLightElement(Object o) {
    return o instanceof LightElement && !(o instanceof GrPropertyForCompletion) && !(o instanceof GrAccessorMethod);
  }

  private static boolean isTrashMethod(PsiMember o) {
    final PsiClass containingClass = o.getContainingClass();
    return containingClass != null && TRASH_CLASSES.contains(containingClass.getQualifiedName());
  }

  private static boolean isAccessor(PsiMember member) {
    return member instanceof PsiMethod && (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)member) || "setProperty".equals(((PsiMethod)member).getName()));
  }


  private static boolean isQualifierClassMember(PsiMember member, PsiElement qualifier) {
    if (!(qualifier instanceof GrExpression)) return false;

    final PsiType type = ((GrExpression)qualifier).getType();
    if (!(type instanceof PsiClassType)) return false;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return false;

    return qualifier.getManager().areElementsEquivalent(member.getContainingClass(), psiClass);
  }

  private enum NotQualifiedKind {
    arrayType,
    innerClass,
    unknown,
    accessor,
    member,
    currentClassMember,
    restrictedClass,
    local,
    onTop
  }

  private enum QualifiedKind {
    innerClass,
    unknown,
    accessor,
    member,
    currentClassMember,
    enumConstant,
  }
}
