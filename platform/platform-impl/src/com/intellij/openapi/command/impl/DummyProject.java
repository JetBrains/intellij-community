// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.PicoContainer;

/**
 * @author max
 */
public class DummyProject extends UserDataHolderBase implements Project {
  private static class DummyProjectHolder {
    private static final DummyProject ourInstance = new DummyProject();
  }

  @NotNull
  public static Project getInstance() {
    return DummyProjectHolder.ourInstance;
  }

  private DummyProject() { }

  @Override
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return "";
  }

  @Override
  @NotNull
  public String getLocationHash() {
    return "dummy";
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getProjectFilePath() {
    return null;
  }

  @Override
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  @Nullable
  @SystemIndependent
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public void save() { }

  @Override
  public BaseComponent getComponent(@NotNull String name) {
    return null;
  }

  @Nullable
  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return null;
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    return false;
  }

  @Override
  @NotNull
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    @SuppressWarnings("unchecked") T[] components = (T[])ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    return components;
  }

  @Override
  @NotNull
  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
    return null;
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  @NotNull
  public Condition<?> getDisposed() {
    return o -> isDisposed();
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() { }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }
}