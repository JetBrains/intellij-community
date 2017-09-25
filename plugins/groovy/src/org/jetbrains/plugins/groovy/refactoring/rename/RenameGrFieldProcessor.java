/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.refactoring.rename.RenameHelperKt.getNewNameFromTransformations;
import static org.jetbrains.plugins.groovy.refactoring.rename.RenameHelperKt.isQualificationNeeded;

/**
 * @author ilyas
 */
public class RenameGrFieldProcessor extends RenameJavaVariableProcessor {

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    assert element instanceof GrField;

    ArrayList<PsiReference> refs = new ArrayList<>();

    GrField field = (GrField)element;
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
    PsiMethod setter = field.getSetter();
    if (setter != null) {
      refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(MethodReferencesSearch.search(setter, projectScope, true).findAll(), setter));
    }
    GrAccessorMethod[] getters = field.getGetters();
    for (GrAccessorMethod getter : getters) {
      refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(MethodReferencesSearch.search(getter, projectScope, true).findAll(), getter));
    }
    refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(ReferencesSearch.search(field, projectScope, false).findAll(), field));
    return refs;
  }

  @Override
  public void renameElement(final PsiElement psiElement,
                            String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    GrField field = (GrField)psiElement;
    Map<GrReferenceExpression, PsiElement> handled = ContainerUtil.newHashMap();

    for (UsageInfo usage : usages) {
      final PsiReference ref = usage.getReference();
      if (ref instanceof GrReferenceExpression) {
        PsiElement resolved = ref.resolve();
        ref.handleElementRename(getNewNameFromTransformations(resolved, newName));
        handled.put((GrReferenceExpression)ref, resolved);
      }
      else if (ref != null) {
        handleElementRename(newName, ref, field.getName());
      }
    }

    field.setName(newName);

    PsiManager manager = psiElement.getManager();
    for (GrReferenceExpression expression : handled.keySet()) {
      PsiElement oldResolved = handled.get(expression);
      if (oldResolved == null) continue;
      PsiElement resolved = expression.resolve();
      if (resolved == null) continue;
      if (manager.areElementsEquivalent(oldResolved, resolved)) continue;
      if (oldResolved.equals(field) || isQualificationNeeded(manager, oldResolved, resolved)) {
        qualify(field, expression);
      }
    }

    if (listener != null) {
      listener.elementRenamed(psiElement);
    }
  }

  private static void handleElementRename(String newName, PsiReference ref, String fieldName) {
    final String refText;

    if (ref instanceof PsiQualifiedReference) {
      refText = ((PsiQualifiedReference)ref).getReferenceName();
    }
    else {
      refText = ref.getCanonicalText();
    }

    String toRename;
    if (fieldName.equals(refText)) {
      toRename = newName;
    }
    else if (GroovyPropertyUtils.getGetterNameNonBoolean(fieldName).equals(refText)) {
      toRename = GroovyPropertyUtils.getGetterNameNonBoolean(newName);
    }
    else if (GroovyPropertyUtils.getGetterNameBoolean(fieldName).equals(refText)) {
      toRename = GroovyPropertyUtils.getGetterNameBoolean(newName);
    }
    else if (GroovyPropertyUtils.getSetterName(fieldName).equals(refText)) {
      toRename = GroovyPropertyUtils.getSetterName(newName);
    }
    else {
      toRename = newName;
    }
    ref.handleElementRename(toRename);
  }

  private static void qualify(PsiMember member, GrReferenceExpression refExpr) {
    String name = refExpr.getReferenceName();
    final PsiClass clazz = member.getContainingClass();
    if (clazz == null) return;

    if (refExpr.getQualifierExpression() != null) return;

    final PsiElement replaced;
    if (member.hasModifierProperty(PsiModifier.STATIC)) {
      final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
        .createReferenceExpressionFromText(clazz.getQualifiedName() + "." + name);
      replaced = refExpr.replace(newRefExpr);
    }
    else {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (member.getManager().areElementsEquivalent(containingClass, clazz)) {
        final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
          .createReferenceExpressionFromText("this." + name);
        replaced = refExpr.replace(newRefExpr);
      }
      else {
        final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
          .createReferenceExpressionFromText(clazz.getQualifiedName() + ".this." + name);
        replaced = refExpr.replace(newRefExpr);
      }
    }
    JavaCodeStyleManager.getInstance(replaced.getProject()).shortenClassReferences(replaced);
  }

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof GrField;
  }

  @Override
  public void findCollisions(PsiElement element,
                             String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    List<UsageInfo> collisions = new ArrayList<>();

    for (UsageInfo info : result) {
      if (!(info instanceof MoveRenameUsageInfo)) continue;
      final PsiElement infoElement = info.getElement();
      final PsiElement referencedElement = ((MoveRenameUsageInfo)info).getReferencedElement();

      if (!(infoElement instanceof GrReferenceExpression)) continue;

      final GrReferenceExpression refExpr = (GrReferenceExpression)infoElement;

      if (!(referencedElement instanceof GrField || refExpr.advancedResolve().isInvokedOnProperty())) continue;

      if (!(refExpr.getParent() instanceof GrCall)) continue;

      final PsiType[] argTypes = PsiUtil.getArgumentTypes(refExpr, false);
      final PsiType[] typeArguments = refExpr.getTypeArguments();
      final MethodResolverProcessor processor =
        new MethodResolverProcessor(newName, refExpr, false, null, argTypes, typeArguments);
      final PsiMethod resolved = ResolveUtil.resolveExistingElement(refExpr, processor, PsiMethod.class);
      if (resolved == null) continue;

      collisions.add(new UnresolvableCollisionUsageInfo(resolved, refExpr) {
        @Override
        public String getDescription() {
          return GroovyRefactoringBundle.message("usage.will.be.overriden.by.method", refExpr.getParent().getText(), PsiFormatUtil
            .formatMethod(resolved, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_TYPE));
        }
      });
    }
    result.addAll(collisions);
    super.findCollisions(element, newName, allRenames, result);
  }

  @Override
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    super.findExistingNameConflicts(element, newName, conflicts);

    GrField field = (GrField)element;
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return;

    final PsiMethod getter = GroovyPropertyUtils.findGetterForField(field);
    if (getter instanceof GrAccessorMethod) {
      final PsiMethod newGetter =
        PropertyUtilBase.findPropertyGetter(containingClass, newName, field.hasModifierProperty(PsiModifier.STATIC), true);
      if (newGetter != null && !(newGetter instanceof GrAccessorMethod)) {
        conflicts.putValue(newGetter, GroovyRefactoringBundle
          .message("implicit.getter.will.by.overriden.by.method", field.getName(), newGetter.getName()));
      }
    }
    final PsiMethod setter = GroovyPropertyUtils.findSetterForField(field);
    if (setter instanceof GrAccessorMethod) {
      final PsiMethod newSetter =
        PropertyUtilBase.findPropertySetter(containingClass, newName, field.hasModifierProperty(PsiModifier.STATIC), true);
      if (newSetter != null && !(newSetter instanceof GrAccessorMethod)) {
        conflicts.putValue(newSetter, GroovyRefactoringBundle
          .message("implicit.setter.will.by.overriden.by.method", field.getName(), newSetter.getName()));
      }
    }
  }
}
