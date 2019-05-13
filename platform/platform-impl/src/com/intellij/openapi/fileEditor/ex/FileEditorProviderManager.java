/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class FileEditorProviderManager{
  public static FileEditorProviderManager getInstance(){
    return ServiceManager.getService(FileEditorProviderManager.class);
  }

  /**
   * @param file cannot be {@code null}
   *
   * @return array of all editors providers that can create editor
   * for the specified {@code file}. The method returns
   * an empty array if there are no such providers. Please note that returned array
   * is constructed with respect to editor policies.
   */
  @NotNull
  public abstract FileEditorProvider[] getProviders(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * @return may be null
   */
  @Nullable
  public abstract FileEditorProvider getProvider(@NotNull String editorTypeId);
}
