// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dgm.GdkMethodHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class SpockMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (aClass == null) {
      return;
    }
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    boolean shouldProcessProperties = ResolveUtil.shouldProcessProperties(classHint);
    boolean shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint);
    if (!shouldProcessMethods && !shouldProcessProperties) return;
    GrMethod method = PsiTreeUtil.getParentOfType(place, GrMethod.class);
    if (method == null) return;
    if (shouldProcessProperties && SpecificationKt.isSpockSpecification(aClass) && aClass == method.getContainingClass()) {
      Map<String, SpockVariableDescriptor> cachedValue = SpockUtils.getVariableMap(method);

      String nameHint = ResolveUtil.getNameHint(processor);
      if (nameHint == null) {
        for (SpockVariableDescriptor spockVar : cachedValue.values()) {
          if (!processor.execute(spockVar.getVariable(), state)) return;
        }
      }
      else {
        SpockVariableDescriptor spockVar = cachedValue.get(nameHint);
        if (spockVar != null && spockVar.getNavigationElement() != place) {
          processor.execute(spockVar.getVariable(), state);
        }
        if (UNDERSCORES.matcher(nameHint).matches()) {
          GrLightVariable variable = new GrLightVariable(place.getManager(), nameHint, PsiType.NULL, List.of(), method);
          processor.execute(variable, state);
        }
      }
    }
    if (shouldProcessMethods) {
      processUseAnnotation(qualifierType, place, processor, state);
    }
  }

  private static void processUseAnnotation(@NotNull PsiType qualifierType,
                                           @NotNull PsiElement place,
                                           @NotNull PsiScopeProcessor processor,
                                           @NotNull ResolveState state) {
    List<PsiAnnotation> annotations = new ArrayList<>();
    List<PsiModifierListOwner> parents = PsiTreeUtil.collectParents(place, PsiModifierListOwner.class, true, (element) -> false);
    for (PsiModifierListOwner parent : parents) {
      PsiModifierList modifierList = parent.getModifierList();
      if (modifierList == null) {
        continue;
      }
      PsiAnnotation anno = modifierList.findAnnotation(SPOCK_UTIL_MOP_USE);
      if (anno != null) {
        annotations.add(anno);
      }
    }
    if (annotations.isEmpty()) {
      return;
    }
    List<PsiClass> categoryClasses = ContainerUtil.flatMap(annotations, (annotation) -> GrAnnotationUtil.getClassArrayValue(annotation, "value", true));
    if (categoryClasses.isEmpty()) {
      return;
    }
    List<GdkMethodHolder> holders = ContainerUtil.map(categoryClasses, (categoryClass) -> GdkMethodHolder.getHolderForClass(categoryClass, true));
    for (GdkMethodHolder categoryHolder : holders) {
      if (!categoryHolder.processMethods(processor, state, qualifierType, place.getProject())) {
        return;
      }
    }
  }

  @Override
  public String getParentClassName() {
    return null;
  }

  private static final Pattern UNDERSCORES = Pattern.compile("^__+$");
  private static final String SPOCK_UTIL_MOP_USE = "spock.util.mop.Use";
}
