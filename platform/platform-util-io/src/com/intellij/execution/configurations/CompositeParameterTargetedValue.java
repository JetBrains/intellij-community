// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.util.containers.ContainerUtil;
import io.netty.bootstrap.com.intellij.execution.configurations.ParameterTargetValue;
import io.netty.bootstrap.com.intellij.execution.configurations.ParameterTargetValuePart;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
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
public class CompositeParameterTargetedValue {
  private final List<ParameterTargetValuePart> myValues = new ArrayList<>();

  public CompositeParameterTargetedValue() {
  }

  public CompositeParameterTargetedValue(@NotNull String value) {
    this();
    addLocalPart(value);
  }

  @NotNull
  public CompositeParameterTargetedValue addLocalPart(@NotNull String value) {
    myValues.add(new ParameterTargetValuePart.Const(value));
    return this;
  }

  @NotNull
  public CompositeParameterTargetedValue addPathPart(@NotNull String localPath) {
    myValues.add(new ParameterTargetValuePart.Path(localPath));
    return this;
  }

  @NotNull
  public CompositeParameterTargetedValue addPathPart(@NotNull File file) {
    myValues.add(new ParameterTargetValuePart.Path(file));
    return this;
  }

  @NotNull
  public CompositeParameterTargetedValue addTargetPart(@NotNull String localValue, @NotNull Promise<String> remoteValue) {
    myValues.add(new ParameterTargetValuePart.PromiseValue(localValue, remoteValue));
    return this;
  }

  @NotNull
  public String getLocalValue() {
    if (myValues.isEmpty()) return "";
    if (myValues.size() == 1) return myValues.get(0).getLocalValue();
    StringBuilder value = new StringBuilder();
    for (ParameterTargetValue parameterTargetValue : myValues) {
      value.append(parameterTargetValue.getLocalValue());
    }
    return value.toString();
  }

  @NotNull
  public List<ParameterTargetValuePart> getParts() {
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

  @NotNull
  @Contract(pure = true)
  public static Collection<? extends CompositeParameterTargetedValue> targetizeParameters(List<String> parameters) {
    return ContainerUtil.map(parameters, parameter -> new CompositeParameterTargetedValue(parameter));
  }
}
