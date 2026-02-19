// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultExternalTask implements ExternalTask {
  private @NotNull String name;
  private @NotNull String qName;
  private @Nullable String description;
  private @Nullable String group;
  private @Nullable String type;

  private boolean isJvm;

  private boolean isTest;

  private boolean isJvmTest;

  private boolean isInherited;

  public DefaultExternalTask() {
  }

  public DefaultExternalTask(@NotNull ExternalTask externalTask) {
    name = externalTask.getName();
    qName = externalTask.getQName();
    description = externalTask.getDescription();
    group = externalTask.getGroup();
    type = externalTask.getType();
    isJvm = externalTask.isJvm();
    isTest = externalTask.isTest();
    isJvmTest = externalTask.isJvmTest();
    isInherited = externalTask.isInherited();
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  @Override
  public @NotNull String getQName() {
    return qName;
  }

  public void setQName(@NotNull String QName) {
    qName = QName;
  }

  @Override
  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  @Override
  public @Nullable String getGroup() {
    return group;
  }

  public void setGroup(@Nullable String group) {
    this.group = group;
  }

  @Override
  public @Nullable String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  @Override
  public boolean isJvm() {
    return isJvm;
  }

  public void setJvm(boolean isJvm) {
    this.isJvm = isJvm;
  }

  @Override
  public boolean isTest() {
    return isTest;
  }

  public void setTest(boolean test) {
    isTest = test;
  }

  @Override
  public boolean isJvmTest() {
    return isJvmTest;
  }

  public void setJvmTest(boolean test) {
    isJvmTest = test;
  }

  @Override
  public boolean isInherited() {
    return isInherited;
  }

  public void setInherited(boolean inherited) {
    isInherited = inherited;
  }
}
