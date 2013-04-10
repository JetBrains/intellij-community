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
package com.intellij.appengine.enhancement;

import com.intellij.openapi.compiler.generic.CompileItem;
import com.intellij.openapi.compiler.generic.DummyPersistentState;
import com.intellij.openapi.compiler.generic.VirtualFileWithDependenciesState;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ClassFileItem extends CompileItem<String, VirtualFileWithDependenciesState, DummyPersistentState> {
  private final VirtualFile myClassFile;
  private final VirtualFile mySourceFile;
  private final List<VirtualFile> myDependencies;

  public ClassFileItem(VirtualFile classFile, VirtualFile sourceFile, List<VirtualFile> dependencies) {
    myClassFile = classFile;
    mySourceFile = sourceFile;
    myDependencies = dependencies;
  }

  @NotNull
  @Override
  public String getKey() {
    return myClassFile.getUrl();
  }

  @Override
  public boolean isSourceUpToDate(@NotNull VirtualFileWithDependenciesState state) {
    return state.isUpToDate(myClassFile);
  }

  @NotNull
  @Override
  public VirtualFileWithDependenciesState computeSourceState() {
    final VirtualFileWithDependenciesState state = new VirtualFileWithDependenciesState(myClassFile.getTimeStamp());
    for (VirtualFile dependency : myDependencies) {
      state.addDependency(dependency);
    }
    return state;
  }

  @Override
  public boolean isOutputUpToDate(@NotNull DummyPersistentState dummyPersistentState) {
    return true;
  }

  @NotNull
  @Override
  public DummyPersistentState computeOutputState() {
    return DummyPersistentState.INSTANCE;
  }

  @NotNull
  public VirtualFile getFile() {
    return myClassFile;
  }

  public VirtualFile getSourceFile() {
    return mySourceFile;
  }
}
