/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  private DummyProject() {
  }

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
  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return null;
  }

  @Override
  @NotNull
  @NonNls
  public String getLocationHash() {
    return "dummy";
  }

  @Override
  @Nullable
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
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public void save() {
  }

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
    return (T[]) ArrayUtil.EMPTY_OBJECT_ARRAY;
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
  public Condition getDisposed() {
    return new Condition() {
      @Override
      public boolean value(final Object o) {
        return isDisposed();
      }
    };
  }

  @NotNull
  public ComponentConfig[] getComponentConfigurations() {
    return new ComponentConfig[0];
  }

  @Nullable
  public Object getComponent(final ComponentConfig componentConfig) {
    return null;
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
  public void dispose() {
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }

  public ComponentConfig getConfig(Class componentImplementation) {
    throw new UnsupportedOperationException("Method getConfig not implemented in " + getClass());
  }
}
