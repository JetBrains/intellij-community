/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  public static Project getInstance() {
    return DummyProjectHolder.ourInstance;
  }

  private DummyProject() {
  }

  public VirtualFile getProjectFile() {
    return null;
  }

  @NotNull
  public String getName() {
    return "";
  }

  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return null;
  }

  @NotNull
  @NonNls
  public String getLocationHash() {
    return "dummy";
  }

  @Nullable
  @NonNls
  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @NotNull
  public String getProjectFilePath() {
    return "";
  }

  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  public void save() {
  }

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return null;
  }

  public boolean hasComponent(@NotNull Class interfaceClass) {
    return false;
  }

  @NotNull
  public <T> T[] getComponents(Class<T> baseClass) {
    return (T[]) ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return null;
  }

  public boolean isDisposed() {
    return false;
  }

  @NotNull
  public Condition getDisposed() {
    return new Condition() {
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

  public boolean isOpen() {
    return false;
  }

  public boolean isInitialized() {
    return false;
  }

  public boolean isDefault() {
    return false;
  }

  public MessageBus getMessageBus() {
    return null;
  }

  public void dispose() {
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }

  public ComponentConfig getConfig(Class componentImplementation) {
    throw new UnsupportedOperationException("Method getConfig not implemented in " + getClass());
  }
}
