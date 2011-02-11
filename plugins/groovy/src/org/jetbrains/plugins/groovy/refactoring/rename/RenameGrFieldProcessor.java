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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
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

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.findGetterForField;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.findSetterForField;

/**
 * @author ilyas
 */
public class RenameGrFieldProcessor extends RenameJavaVariableProcessor {

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    assert element instanceof GrField;

    ArrayList<PsiReference> refs = new ArrayList<PsiReference>();

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
    refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(ReferencesSearch.search(field, projectScope, true).findAll(), field));
    return refs;
  }

  @Override
  public void renameElement(final PsiElement psiElement,
                            String newName,
                            final UsageInfo[] usages,
                            final RefactoringElementListener listener) throws IncorrectOperationException {
    Map<PsiElement, String> renames = new HashMap<PsiElement, String>();
    renames.put(psiElement, newName);

    final GrField field = (GrField)psiElement;
    String fieldName = field.getName();
    for (GrAccessorMethod getter : field.getGetters()) {
      renames.put(getter, RenamePropertyUtil.getGetterNameByOldName(newName, getter.getName()));
    }
    final GrAccessorMethod setter = field.getSetter();
    if (setter != null) {
      renames.put(setter, GroovyPropertyUtils.getSetterName(newName));
    }

    MultiMap<PsiNamedElement, UsageInfo> propertyUsages = new MultiMap<PsiNamedElement, UsageInfo>();
    MultiMap<PsiNamedElement, UsageInfo> simpleUsages = new MultiMap<PsiNamedElement, UsageInfo>();

    for (UsageInfo usage : usages) {
      final PsiReference ref = usage.getReference();
      if (ref instanceof GrReferenceExpression) {
        final GroovyResolveResult resolveResult = ((GrReferenceExpression)ref).advancedResolve();
        final PsiElement element = resolveResult.getElement();
        if (resolveResult.isInvokedOnProperty()) {
          if (element == null) {
            handleElementRename(newName, ref, fieldName);
          }
          else {
            propertyUsages.putValue((PsiNamedElement)element, usage);
          }
        }
        else {
          simpleUsages.putValue((PsiNamedElement)element, usage);
        }
      }
      else if (ref != null) {
        handleElementRename(newName, ref, fieldName);
      }
    }

    field.setName(newName);
    final GrAccessorMethod[] newGetters = field.getGetters();
    final GrAccessorMethod newSetter = field.getSetter();
    Map<String, PsiNamedElement> newElements = new HashMap<String, PsiNamedElement>();
    newElements.put(newName, field);
    for (GrAccessorMethod newGetter : newGetters) {
      newElements.put(newGetter.getName(), newGetter);
    }
    if (newSetter != null) {
      newElements.put(newSetter.getName(), newSetter);
    }

    PsiManager manager = field.getManager();
    for (PsiNamedElement element : simpleUsages.keySet()) {
      for (UsageInfo info : simpleUsages.get(element)) {
        final String name = renames.get(element);
        rename(newElements.get(name), info, name == null ? newName : name, name != null, manager);
      }
    }
    for (PsiNamedElement element : propertyUsages.keySet()) {
      for (UsageInfo info : propertyUsages.get(element)) {
        rename(element, info, newName, true, manager);
      }
    }
    simpleUsages.clear();
    propertyUsages.clear();
    renames.clear();
    listener.elementRenamed(field);
  }

  private static void rename(PsiNamedElement element,
                             UsageInfo info,
                             String nameToUse,
                             boolean shouldCheckForCorrectResolve,
                             PsiManager manager) {
    final PsiReference ref = info.getReference();
    final PsiElement renamed = ref.handleElementRename(nameToUse);
    PsiElement newly_resolved = ref.resolve();
    if (shouldCheckForCorrectResolve && !(manager.areElementsEquivalent(element, newly_resolved))) {
      qualify((PsiMember)element, renamed, nameToUse);
    }
  }

  private static void handleElementRename(String newName, PsiReference ref, String fieldName) {
    final String refText = ref.getCanonicalText();
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

  private static void qualify(PsiMember member, PsiElement renamed, String name) {
    if (!(renamed instanceof GrReferenceExpression)) return;

    final PsiClass clazz = member.getContainingClass();
    if (clazz == null) return;

    final GrReferenceExpression refExpr = (GrReferenceExpression)renamed;
    if (refExpr.getQualifierExpression() != null) return;

    final PsiElement replaced;
    if (member.hasModifierProperty(GrModifier.STATIC)) {
      final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
        .createReferenceExpressionFromText(clazz.getQualifiedName() + "." + name);
      replaced = refExpr.replace(newRefExpr);
    }
    else {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(renamed, PsiClass.class);
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
    PsiUtil.shortenReferences((GroovyPsiElement)replaced);
  }

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof GrField && ((GrField)element).isProperty() || element instanceof GrAccessorMethod;
  }

  @Override
  public void findCollisions(PsiElement element,
                             String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    List<UsageInfo> collisions = new ArrayList<UsageInfo>();

    for (UsageInfo info : result) {
      if (!(info instanceof MoveRenameUsageInfo)) continue;
      final PsiElement infoElement = info.getElement();
      final PsiElement referencedElement = ((MoveRenameUsageInfo)info).getReferencedElement();
      if (infoElement instanceof GrReferenceExpression &&
          (referencedElement instanceof GrField || ((GrReferenceExpression)infoElement).advancedResolve().isInvokedOnProperty()) &&
          infoElement.getParent() instanceof GrCall) {
        final PsiType[] argTypes = PsiUtil.getArgumentTypes(infoElement, false);
        final PsiMethod resolved = ResolveUtil.resolveExistingElement(((GrReferenceExpression)infoElement),
                                                                      new MethodResolverProcessor(newName, ((GroovyPsiElement)infoElement),
                                                                                                  false, null, argTypes,
                                                                                                  ((GrReferenceExpression)infoElement)
                                                                                                    .getTypeArguments()), PsiMethod.class);
        if (resolved != null) {
          collisions.add(new UnresolvableCollisionUsageInfo(resolved, infoElement) {
            @Override
            public String getDescription() {
              return GroovyRefactoringBundle.message("usage.will.be.overriden.by.method", infoElement.getParent().getText(), PsiFormatUtil
                .formatMethod(resolved, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME, PsiFormatUtil.SHOW_TYPE));
            }
          });
        }
      }
    }
    result.addAll(collisions);
    super
      .findCollisions(element, newName, allRenames, result);
  }

  @Override
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    super.findExistingNameConflicts(element, newName, conflicts);

    GrField field = (GrField)element;
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return;

    final PsiMethod getter = findGetterForField(field);
    if (getter instanceof GrAccessorMethod) {
      final PsiMethod newGetter =
        PropertyUtil.findPropertyGetter(containingClass, newName, field.hasModifierProperty(GrModifier.STATIC), true);
      if (newGetter != null && !(newGetter instanceof GrAccessorMethod)) {
        conflicts.putValue(newGetter, GroovyRefactoringBundle
          .message("implicit.getter.will.by.overriden.by.method", field.getName(), newGetter.getName()));
      }
    }
    final PsiMethod setter = findSetterForField(field);
    if (setter instanceof GrAccessorMethod) {
      final PsiMethod newSetter =
        PropertyUtil.findPropertySetter(containingClass, newName, field.hasModifierProperty(GrModifier.STATIC), true);
      if (newSetter != null && !(newSetter instanceof GrAccessorMethod)) {
        conflicts.putValue(newSetter, GroovyRefactoringBundle
          .message("implicit.setter.will.by.overriden.by.method", field.getName(), newSetter.getName()));
      }
    }
  }

  protected interface ImplicitMember {
    /**
     * identifiers type of implicit member (e.g. getter, setter, tagLibVariable). Should be equal for all members of one type.
     * @return identifier
     */
    @NotNull
    Object getIdentifier();

    /**
     * generates name of member by field.
     * may be null, if member is not available for field
     * @param field
     * @return
     */
    @Nullable
    String getName(GrField field);

    /**
     * may be null, if member is not available for field
     * @return an instance of member for field
     */
    @Nullable
    PsiElement getMember(GrField field);
  }
}
