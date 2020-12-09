/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a fully resolved property, that is it takes a property and provides an interface that squishes any references.
 * This for for use in models other than the Ext model, since in these we do not care about the reference chain of the property
 * and just want to get the actual value. Using {@link GradlePropertyModelImpl} directly can result in having to follow long
 * reference changes in order to get a value.
 */
public class ResolvedPropertyModelImpl implements ResolvedPropertyModel {
  @NotNull protected final GradlePropertyModelImpl myRealModel;

  public ResolvedPropertyModelImpl(@NotNull GradlePropertyModelImpl realModel) {
    myRealModel = realModel;
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return resolveModel().getValueType();
  }

  @NotNull
  @Override
  public PropertyType getPropertyType() {
    return myRealModel.getPropertyType();
  }

  @Nullable
  @Override
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    return resolveModel().getValue(typeReference);
  }

  @Nullable
  @Override
  public <T> T getRawValue(@NotNull TypeReference<T> typeReference) {
    return myRealModel.getRawValue(typeReference);
  }

  @NotNull
  @Override
  public List<GradlePropertyModel> getDependencies() {
    return myRealModel.getDependencies();
  }

  @NotNull
  @Override
  public String getName() {
    return myRealModel.getName();
  }

  @NotNull
  @Override
  public String getFullyQualifiedName() {
    return myRealModel.getFullyQualifiedName();
  }

  @NotNull
  @Override
  public VirtualFile getGradleFile() {
    return myRealModel.getGradleFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    myRealModel.setValue(value);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel convertToEmptyMap() {
    myRealModel.convertToEmptyMap();
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel getMapValue(@NotNull String key) {
    return myRealModel.getMapValue(key);
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyList() {
    myRealModel.convertToEmptyList();
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel addListValue() {
    return myRealModel.addListValue();
  }

  @Override
  @NotNull
  public GradlePropertyModel addListValueAt(int index) {
    return myRealModel.addListValueAt(index);
  }

  @Nullable
  @Override
  public GradlePropertyModel getListValue(@NotNull Object value) {
    return myRealModel.getListValue(value);
  }

  @Override
  public void delete() {
    myRealModel.delete();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel resolve() {
    return this;
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    return myRealModel.getPsiElement();
  }

  @Nullable
  @Override
  public PsiElement getExpressionPsiElement() {
    return myRealModel.getExpressionPsiElement();
  }

  @Nullable
  @Override
  public PsiElement getFullExpressionPsiElement() {
    return myRealModel.getFullExpressionPsiElement();
  }

  @Nullable
  @Override
  public String toString() {
    return resolveModel().toString();
  }

  @Nullable
  @Override
  public String valueAsString() {
    return resolveModel().valueAsString();
  }

  @NotNull
  @Override
  public String forceString() {
    return resolveModel().forceString();
  }

  @Nullable
  @Override
  public Integer toInt() {
    return resolveModel().toInt();
  }

  @Nullable
  @Override
  public BigDecimal toBigDecimal() {
    return resolveModel().toBigDecimal();
  }

  @Nullable
  @Override
  public Boolean toBoolean() {
    return resolveModel().toBoolean();
  }

  @Nullable
  @Override
  public List<GradlePropertyModel> toList() {
    List<GradlePropertyModel> list = resolveModel().toList();
    if (list == null) {
      return null;
    }
    return ContainerUtil.map(list, GradlePropertyModel::resolve);
  }

  @Nullable
  @Override
  public Map<String, GradlePropertyModel> toMap() {
    Map<String, GradlePropertyModel> map = resolveModel().toMap();
    if (map == null) {
      return null;
    }
    return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().resolve()));
  }

  @Override
  public void rename(@NotNull String name) {
    myRealModel.rename(name);
  }

  @Override
  public void rename(@NotNull List<String> hierarchicalName) {
    myRealModel.rename(hierarchicalName);
  }

  @Override
  public boolean isModified() {
    return myRealModel.isModified();
  }

  @Override
  @NotNull
  public GradlePropertyModel getUnresolvedModel() {
    return myRealModel;
  }

  @Override
  @Nullable
  public GradleDslElement getRawElement() {
    return myRealModel.getRawElement();
  }

  @NotNull
  @Override
  public GradlePropertyModel getResultModel() {
    return resolveModel();
  }

  @NotNull
  protected GradlePropertyModelImpl resolveModel() {
    return PropertyUtil.resolveModel(myRealModel);
  }
}
