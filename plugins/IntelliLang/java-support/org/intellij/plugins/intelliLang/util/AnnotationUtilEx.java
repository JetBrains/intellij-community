/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains some extended utility functions for dealing with annotations.
 */
public class AnnotationUtilEx {
  private static final PsiConstantEvaluationHelperImpl CONSTANT_EVALUATION_HELPER = new PsiConstantEvaluationHelperImpl();

  private AnnotationUtilEx() {
  }

  /**
   * @see AnnotationUtilEx#getAnnotatedElementFor(PsiElement, LookupType)
   */
  public enum LookupType {
    PREFER_CONTEXT, PREFER_DECLARATION, CONTEXT_ONLY, DECLARATION_ONLY
  }

  /**
   * Determines the PsiModifierListOwner for the passed element depending of the specified LookupType. The LookupType
   * decides whether to prefer the element a reference expressions resolves to, or the element that is implied by the
   * usage context ("expected type").
   */
  @Nullable
  public static PsiModifierListOwner getAnnotatedElementFor(@Nullable PsiElement element, LookupType type) {
    while (element != null) {
      if (type == LookupType.PREFER_DECLARATION || type == LookupType.DECLARATION_ONLY) {
        if (element instanceof PsiReferenceExpression) {
          final PsiElement e = ((PsiReferenceExpression)element).resolve();
          if (e instanceof PsiModifierListOwner) {
            return (PsiModifierListOwner)e;
          }
          if (type == LookupType.DECLARATION_ONLY) {
            return null;
          }
        }
      }
      element = ContextComputationProcessor.getTopLevelInjectionTarget(element);
      final PsiElement parent = element.getParent();

      if (element instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)element).getOperationTokenType() == JavaTokenType.PLUSEQ) {
        element = ((PsiAssignmentExpression)element).getLExpression();
        continue;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression p = (PsiAssignmentExpression)parent;
        if (p.getRExpression() == element) {
          element = p.getLExpression();
          continue;
        }
      }
      else if (parent instanceof PsiReturnStatement) {
        final PsiMethod m = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
        if (m != null) {
          return m;
        }
      }
      else if (parent instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner)parent;
      }
      else if (parent instanceof PsiArrayInitializerMemberValue) {
        final PsiArrayInitializerMemberValue value = (PsiArrayInitializerMemberValue)parent;
        final PsiElement pair = value.getParent();
        if (pair instanceof PsiNameValuePair) {
          return AnnotationUtil.getAnnotationMethod((PsiNameValuePair)pair);
        }
      }
      else if (parent instanceof PsiNameValuePair) {
        return AnnotationUtil.getAnnotationMethod((PsiNameValuePair)parent);
      }
      else {
        return PsiUtilEx.getParameterForArgument(element);
      }

      // If no annotation has been found through the usage context, check if the element
      // (i.e. the element the reference refers to) is annotated itself
      if (type != LookupType.DECLARATION_ONLY) {
        if (element instanceof PsiReferenceExpression) {
          final PsiElement e = ((PsiReferenceExpression)element).resolve();
          if (e instanceof PsiModifierListOwner) {
            return (PsiModifierListOwner)e;
          }
        }
      }
      return null;
    }
    return null;
  }

  public interface AnnotatedElementVisitor {
    boolean visitMethodParameter(PsiExpression expression, PsiCall psiCallExpression);
    boolean visitMethodReturnStatement(PsiElement source, PsiMethod method);
    boolean visitVariable(PsiVariable variable);
    boolean visitAnnotationParameter(PsiNameValuePair nameValuePair, PsiAnnotation psiAnnotation);
    boolean visitReference(PsiReferenceExpression expression);
  }

  public static void visitAnnotatedElements(@Nullable PsiElement element, AnnotatedElementVisitor visitor) {
    if (element == null) return;
    for (PsiElement cur = ContextComputationProcessor.getTopLevelInjectionTarget(element); cur != null; cur = cur.getParent()) {
      if (!visitAnnotatedElementInner(cur, visitor)) return;
    }
  }

  private static boolean visitAnnotatedElementInner(PsiElement element, AnnotatedElementVisitor visitor) {
    final PsiElement parent = element.getParent();

    if (element instanceof PsiReferenceExpression) {
      if (parent instanceof PsiReferenceExpression) return false; // skip qualified references
      if (!visitor.visitReference((PsiReferenceExpression)element)) return false;
    }
    else if (element instanceof PsiNameValuePair && parent != null && parent.getParent() instanceof PsiAnnotation) {
      return visitor.visitAnnotationParameter((PsiNameValuePair)element, (PsiAnnotation)parent.getParent());
    }

    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression p = (PsiAssignmentExpression)parent;
      if (p.getRExpression() == element || p.getOperationTokenType() == JavaTokenType.PLUSEQ) {
        final PsiExpression left = p.getLExpression();
        if (left instanceof PsiReferenceExpression) {
          if (!visitor.visitReference((PsiReferenceExpression)left)) return false;
        }
      }
    }
    else if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() == element) {
      return false;
    }
    else if (parent instanceof PsiFunctionalExpression) {
      PsiMethod m = LambdaUtil.getFunctionalInterfaceMethod(parent);
      if (m != null) {
        if (!visitor.visitMethodReturnStatement(element, m)) return false;
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiElement e = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiFunctionalExpression.class);
      PsiMethod m = e == null ? null : e instanceof PsiMethod ? (PsiMethod)e : LambdaUtil.getFunctionalInterfaceMethod(e);
      if (m != null) {
        if (!visitor.visitMethodReturnStatement(parent, m)) return false;
      }
    }
    else if (parent instanceof PsiVariable) {
      return visitor.visitVariable((PsiVariable)parent);
    }
    else if (parent instanceof PsiModifierListOwner) {
      return false; // PsiClass/PsiClassInitializer/PsiCodeBlock
    }
    else if (parent instanceof PsiArrayInitializerMemberValue || parent instanceof PsiNameValuePair) {
      return true;
    }
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCall) {
      return visitor.visitMethodParameter((PsiExpression)element, (PsiCall)parent.getParent());
    }
    return true;
  }

  /**
   * Utility method to obtain annotations of a specific type from the supplied PsiModifierListOwner.
   * For optimization reasons, this method only looks at elements of type java.lang.String.
   * <p/>
   * The parameter {@code allowIndirect} determines if the method should look for indirect annotations, i.e.
   * annotations which have themselves been annotated by the supplied annotation name. Currently, this only allows
   * one level of indirection and returns an array of [base-annotation, indirect annotation]
   * <p/>
   * The {@code annotationName} parameter is a pair of the target annotation class' fully qualified name as a
   * String and as a Set. This is done for performance reasons because the Set is required by the
   * {@link AnnotationUtil} utility class and allows to avoid unnecessary object constructions.
   */
  @NotNull
  public static PsiAnnotation[] getAnnotationFrom(PsiModifierListOwner owner,
                                                  Pair<String, ? extends Set<String>> annotationName,
                                                  boolean allowIndirect,
                                                  boolean inHierarchy) {
    if (!PsiUtilEx.isLanguageAnnotationTarget(owner)) return PsiAnnotation.EMPTY_ARRAY;

    return getAnnotationsFromImpl(owner, annotationName, allowIndirect, inHierarchy);
  }


  /**
   * The parameter {@code allowIndirect} determines if the method should look for indirect annotations, i.e.
   * annotations which have themselves been annotated by the supplied annotation name. Currently, this only allows
   * one level of indirection and returns an array of [base-annotation, indirect annotation]
   * <p/>
   * The {@code annotationName} parameter is a pair of the target annotation class' fully qualified name as a
   * String and as a Set. This is done for performance reasons because the Set is required by the
   * {@link AnnotationUtil} utility class and allows to avoid unnecessary object constructions.
   */

  public static PsiAnnotation[] getAnnotationsFromImpl(PsiModifierListOwner owner,
                                                        Pair<String, ? extends Set<String>> annotationName,
                                                        boolean allowIndirect, boolean inHierarchy) {
    final PsiAnnotation directAnnotation = inHierarchy?
      AnnotationUtil.findAnnotationInHierarchy(owner, annotationName.second) :
      AnnotationUtil.findAnnotation(owner, annotationName.second);
    if (directAnnotation != null) {
      return new PsiAnnotation[]{directAnnotation};
    }
    if (allowIndirect) {
      final PsiAnnotation[] annotations = getAnnotations(owner, inHierarchy);
      for (PsiAnnotation annotation : annotations) {
        PsiJavaCodeReferenceElement nameReference = annotation.getNameReferenceElement();
        if (nameReference == null) continue;
        PsiElement resolved = nameReference.resolve();
        if (resolved instanceof PsiClass) {
          final PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotationInHierarchy((PsiModifierListOwner)resolved, annotationName.second);
          if (psiAnnotation != null) {
            return new PsiAnnotation[]{psiAnnotation, annotation};
          }
        }
      }
    }
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public static PsiAnnotation[] getAnnotationFrom(@NotNull PsiModifierListOwner owner,
                                                  @NotNull Pair<String, ? extends Set<String>> annotationName,
                                                  boolean allowIndirect) {
    return getAnnotationFrom(owner, annotationName, allowIndirect, true);
  }

  /**
   * Calculates the value of the annotation's attribute referenced by the {@code attr} parameter by trying to
   * find the attribute in the supplied list of annotations and calculating the constant value for the first attribute
   * it finds.
   */
  @Nullable
  public static String calcAnnotationValue(PsiAnnotation[] annotation, @NonNls String attr) {
    for (PsiAnnotation psiAnnotation : annotation) {
      final String value = calcAnnotationValue(psiAnnotation, attr);
      if (value != null) return value;
    }
    return null;
  }

  @Nullable
  public static String calcAnnotationValue(@NotNull PsiAnnotation annotation, @NonNls String attr) {
    PsiElement value = annotation.findAttributeValue(attr);
    Object o = CONSTANT_EVALUATION_HELPER.computeConstantExpression(value);
    if (o instanceof String) {
      return (String)o;
    }
    return null;
  }

  /**
   * Returns all annotations for {@code listOwner}, possibly walking up the method hierarchy.
   *
   * @see AnnotationUtil#getSuperAnnotationOwners(PsiModifierListOwner)
   */
  private static PsiAnnotation[] getAnnotations(@NotNull final PsiModifierListOwner listOwner, final boolean inHierarchy) {
    final PsiModifierList modifierList = listOwner.getModifierList();
    if (!inHierarchy) {
      return modifierList == null ? PsiAnnotation.EMPTY_ARRAY : modifierList.getAnnotations();
    }
    return CachedValuesManager.getCachedValue(listOwner, () -> CachedValueProvider.Result
      .create(getHierarchyAnnotations(listOwner), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static PsiAnnotation[] getHierarchyAnnotations(PsiModifierListOwner listOwner) {
    final Set<PsiAnnotation> all = new HashSet<PsiAnnotation>() {
      public boolean add(PsiAnnotation o) {
        // don't overwrite "higher level" annotations
        return !contains(o) && super.add(o);
      }
    };

    PsiModifierList modifierList = listOwner.getModifierList();
    if (modifierList != null) {
      ContainerUtil.addAll(all, modifierList.getAnnotations());
    }
    for (PsiModifierListOwner superOwner : AnnotationUtil.getSuperAnnotationOwners(listOwner)) {
      modifierList = superOwner.getModifierList();
      if (modifierList != null) {
        ContainerUtil.addAll(all, modifierList.getAnnotations());
      }
    }
    return all.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : all.toArray(new PsiAnnotation[all.size()]);
  }
}