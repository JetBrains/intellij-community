/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.coverage;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.Nullable;

@State(
  name = "JavaCoverageOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class JavaCoverageOptionsProvider implements PersistentStateComponent<JavaCoverageOptionsProvider.State> {
  private State myState = new State();
  private Project myProject;

  public JavaCoverageOptionsProvider(Project project) {
    myProject = project;
  }

  public boolean ignoreImplicitConstructors() {
    return myState.myIgnoreImplicitConstructors;
  }

  public static JavaCoverageOptionsProvider getInstance(Project project) {
    return ServiceManager.getService(project, JavaCoverageOptionsProvider.class);
  }

  public void setIgnoreImplicitConstructors(boolean state) {
    myState.myIgnoreImplicitConstructors = state;
  }
  
  public void setIgnoreEmptyPrivateConstructors(boolean state) {
    myState.myIgnoreEmptyPrivateConstructors = state;
  }
  
  public boolean ignoreEmptyPrivateConstructors() {
    return myState.myIgnoreEmptyPrivateConstructors;
  }

  public boolean isGeneratedConstructor(String qualifiedName, String methodSignature) {
    if (myState.myIgnoreImplicitConstructors || myState.myIgnoreEmptyPrivateConstructors) {
      PsiClass psiClass = ReadAction.compute(() -> ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), qualifiedName));
      return PackageAnnotator.isGeneratedDefaultConstructor(psiClass, methodSignature, myState.myIgnoreImplicitConstructors, myState.myIgnoreEmptyPrivateConstructors);
    }
    return false;
  }
  
  @Nullable
  @Override
  public JavaCoverageOptionsProvider.State getState() {
    return myState;
  }

  @Override
  public void loadState(JavaCoverageOptionsProvider.State state) {
     myState.myIgnoreEmptyPrivateConstructors = state.myIgnoreEmptyPrivateConstructors;
     myState.myIgnoreImplicitConstructors = state.myIgnoreImplicitConstructors;
  }
  
  
  public static class State {
    public boolean myIgnoreEmptyPrivateConstructors = true;
    public boolean myIgnoreImplicitConstructors = true;
  }

}
