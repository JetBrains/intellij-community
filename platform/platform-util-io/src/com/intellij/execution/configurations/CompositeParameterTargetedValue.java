// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Analogue of com.intellij.execution.target.value.TargetValue for ParameterList — provides opportunity to provide parameter value
 * for com.intellij.execution.target.TargetEnvironmentConfiguration:
 * provide remote path for uploaded file, or proper port, etc.
 */
@ApiStatus.Experimental
public class CompositeParameterTargetedValue {
  private final List<ParameterTargetValuePart> myValues = new ArrayList<>();

  public CompositeParameterTargetedValue() {
  }

  public CompositeParameterTargetedValue(@NotNull String value) {
    this();
    addLocalPart(value);
  }

  public @NotNull CompositeParameterTargetedValue addLocalPart(@NotNull String value) {
    myValues.add(new ParameterTargetValuePart.Const(value));
    return this;
  }

  public @NotNull CompositeParameterTargetedValue addPathPart(@NotNull String localPath) {
    myValues.add(new ParameterTargetValuePart.Path(localPath));
    return this;
  }

  public @NotNull CompositeParameterTargetedValue addPathSeparator() {
    myValues.add(ParameterTargetValuePart.PathSeparator.INSTANCE);
    return this;
  }

  public @NotNull CompositeParameterTargetedValue addPathPart(@NotNull File file) {
    myValues.add(new ParameterTargetValuePart.Path(file));
    return this;
  }

  public @NotNull CompositeParameterTargetedValue addTargetPart(@NotNull String localValue, @NotNull Promise<String> remoteValue) {
    myValues.add(new ParameterTargetValuePart.PromiseValue(localValue, remoteValue));
    return this;
  }

  public @NotNull String getLocalValue() {
    if (myValues.isEmpty()) return "";
    if (myValues.size() == 1) return myValues.get(0).getLocalValue();
    StringBuilder value = new StringBuilder();
    for (ParameterTargetValue parameterTargetValue : myValues) {
      value.append(parameterTargetValue.getLocalValue());
    }
    return value.toString();
  }

  public @NotNull List<ParameterTargetValuePart> getParts() {
    return Collections.unmodifiableList(myValues);
  }

  @Override
  public String toString() {
    if (myValues.isEmpty()) return "";
    if (myValues.size() == 1) {
      return myValues.get(0).toString();
    }
    return myValues.toString();
  }

  @Contract(pure = true)
  public static @NotNull @Unmodifiable Collection<? extends CompositeParameterTargetedValue> targetizeParameters(List<String> parameters) {
    return ContainerUtil.map(parameters, parameter -> new CompositeParameterTargetedValue(parameter));
  }
}
