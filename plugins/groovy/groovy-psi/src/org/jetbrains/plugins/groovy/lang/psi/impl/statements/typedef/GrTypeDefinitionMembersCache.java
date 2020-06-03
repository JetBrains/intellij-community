// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code.BodyCodeMembersProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code.GrCodeMembersProvider;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.transformations.TransformationResult;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;

import java.util.Collection;
import java.util.Collections;

public class GrTypeDefinitionMembersCache<T extends GrTypeDefinition> {

  private final T myDefinition;
  private final GrCodeMembersProvider<? super T> myCodeMembersProvider;
  private final Collection<?> myDependencies = Collections.singletonList(PsiModificationTracker.MODIFICATION_COUNT);

  public GrTypeDefinitionMembersCache(@NotNull T definition) {
    this(definition, BodyCodeMembersProvider.INSTANCE);
  }

  public GrTypeDefinitionMembersCache(@NotNull T definition, @NotNull GrCodeMembersProvider<? super T> provider) {
    myDefinition = definition;
    myCodeMembersProvider = provider;
  }

  public GrTypeDefinition[] getCodeInnerClasses() {
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      myCodeMembersProvider.getCodeInnerClasses(myDefinition), myDependencies
    )).clone();
  }

  public GrMethod[] getCodeMethods() {
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      myCodeMembersProvider.getCodeMethods(myDefinition), myDependencies
    )).clone();
  }

  public GrMethod[] getCodeConstructors() {
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      GrClassImplUtil.getCodeConstructors(myDefinition), myDependencies
    )).clone();
  }

  public GrField[] getCodeFields() {
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      myCodeMembersProvider.getCodeFields(myDefinition), myDependencies
    )).clone();
  }

  public PsiClass[] getInnerClasses() {
    return getTransformationResult().getInnerClasses().clone();
  }

  public PsiMethod[] getMethods() {
    return getTransformationResult().getMethods().clone();
  }

  public PsiMethod[] getConstructors() {
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      GrClassImplUtil.getConstructors(myDefinition), myDependencies
    )).clone();
  }

  public GrField[] getFields() {
    return getTransformationResult().getFields().clone();
  }

  public PsiClassType @NotNull [] getExtendsListTypes(boolean includeSynthetic) {
    return CachedValuesManager.getCachedValue(myDefinition, includeSynthetic ? () -> {
      PsiClassType[] extendsTypes = getTransformationResult().getExtendsTypes();
      return CachedValueProvider.Result.create(extendsTypes, myDependencies);
    } : () -> {
      PsiClassType[] extendsTypes = GrClassImplUtil.getReferenceListTypes(myDefinition.getExtendsClause());
      return CachedValueProvider.Result.create(extendsTypes, myDependencies);
    }).clone();
  }

  public PsiClassType @NotNull [] getImplementsListTypes(boolean includeSynthetic) {
    return CachedValuesManager.getCachedValue(myDefinition, includeSynthetic ? () -> {
      PsiClassType[] implementsTypes = getTransformationResult().getImplementsTypes();
      return CachedValueProvider.Result.create(implementsTypes, myDependencies);
    } : () -> {
      PsiClassType[] implementsTypes = GrClassImplUtil.getReferenceListTypes(myDefinition.getImplementsClause());
      return CachedValueProvider.Result.create(implementsTypes, myDependencies);
    }).clone();
  }

  @NotNull
  private TransformationResult getTransformationResult() {
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      TransformationUtilKt.transformDefinition(myDefinition), myDependencies
    ));
  }
}