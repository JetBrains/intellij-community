/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code.BodyCodeMembersProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code.GrCodeMembersProvider;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.transformations.TransformationResult;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

public class GrTypeDefinitionMembersCache<T extends GrTypeDefinition> {

  private final SimpleModificationTracker myTreeChangeTracker = new SimpleModificationTracker();
  private final T myDefinition;
  private final GrCodeMembersProvider<? super T> myCodeMembersProvider;
  private final Collection<?> myDependencies = Arrays.asList(myTreeChangeTracker, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);

  public GrTypeDefinitionMembersCache(@NotNull T definition) {
    this(definition, BodyCodeMembersProvider.INSTANCE);
  }

  public GrTypeDefinitionMembersCache(@NotNull T definition, @NotNull GrCodeMembersProvider<? super T> provider) {
    myDefinition = definition;
    myCodeMembersProvider = provider;
  }

  public void dropCaches() {
    myTreeChangeTracker.incModificationCount();
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
    assert !TransformationUtilKt.isUnderTransformation(myDefinition);
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      GrClassImplUtil.getConstructors(myDefinition), myDependencies
    )).clone();
  }

  public GrField[] getFields() {
    return getTransformationResult().getFields().clone();
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes(boolean includeSynthetic) {
    if (includeSynthetic && TransformationUtilKt.isUnderTransformation(myDefinition)) includeSynthetic = false;
    return CachedValuesManager.getCachedValue(myDefinition, includeSynthetic ? () -> {
      PsiClassType[] extendsTypes = getTransformationResult().getExtendsTypes();
      PsiClassType[] result = GrClassImplUtil.getExtendsListTypes(myDefinition, extendsTypes);
      return CachedValueProvider.Result.create(result, myDependencies);
    } : () -> {
      PsiClassType[] extendsTypes = GrClassImplUtil.getReferenceListTypes(myDefinition.getExtendsClause());
      PsiClassType[] result = GrClassImplUtil.getExtendsListTypes(myDefinition, extendsTypes);
      return CachedValueProvider.Result.create(result, myDependencies);
    }).clone();
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes(boolean includeSynthetic) {
    if (includeSynthetic && TransformationUtilKt.isUnderTransformation(myDefinition)) includeSynthetic = false;
    return CachedValuesManager.getCachedValue(myDefinition, includeSynthetic ? () -> {
      PsiClassType[] implementsTypes = getTransformationResult().getImplementsTypes();
      PsiClassType[] result = GrClassImplUtil.getImplementsListTypes(myDefinition, implementsTypes);
      return CachedValueProvider.Result.create(result, myDependencies);
    } : () -> {
      PsiClassType[] implementsTypes = GrClassImplUtil.getReferenceListTypes(myDefinition.getImplementsClause());
      PsiClassType[] result = GrClassImplUtil.getImplementsListTypes(myDefinition, implementsTypes);
      return CachedValueProvider.Result.create(result, myDependencies);
    }).clone();
  }

  @NotNull
  private TransformationResult getTransformationResult() {
    assert !TransformationUtilKt.isUnderTransformation(myDefinition);
    return CachedValuesManager.getCachedValue(myDefinition, () -> CachedValueProvider.Result.create(
      TransformationUtilKt.transformDefinition(myDefinition), myDependencies
    ));
  }
}