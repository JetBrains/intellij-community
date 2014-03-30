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

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.generic.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class EnhancerCompiler extends GenericCompiler<String, VirtualFileWithDependenciesState, DummyPersistentState> {
  public EnhancerCompiler(Project project) {
    super("appengine-enhancer", 0, CompileOrderPlace.CLASS_POST_PROCESSING);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getItemKeyDescriptor() {
    return STRING_KEY_DESCRIPTOR;
  }

  @NotNull
  @Override
  public DataExternalizer<VirtualFileWithDependenciesState> getSourceStateExternalizer() {
    return VirtualFileWithDependenciesState.EXTERNALIZER;
  }

  @NotNull
  @Override
  public DataExternalizer<DummyPersistentState> getOutputStateExternalizer() {
    return DummyPersistentState.EXTERNALIZER;
  }

  @NotNull
  @Override
  public GenericCompilerInstance<?, ? extends CompileItem<String, VirtualFileWithDependenciesState, DummyPersistentState>, String, VirtualFileWithDependenciesState, DummyPersistentState> createInstance(
    @NotNull CompileContext context) {
    return new EnhancerCompilerInstance(context);
  }

  @NotNull
  public String getDescription() {
    return "Google App Engine Enhancer";
  }
}
