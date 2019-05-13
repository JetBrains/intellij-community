// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "JavaCoverageOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class JavaCoverageOptionsProvider implements PersistentStateComponent<JavaCoverageOptionsProvider.State> {
  private final State myState = new State();
  private final Project myProject;

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
  public void loadState(@NotNull JavaCoverageOptionsProvider.State state) {
     myState.myIgnoreEmptyPrivateConstructors = state.myIgnoreEmptyPrivateConstructors;
     myState.myIgnoreImplicitConstructors = state.myIgnoreImplicitConstructors;
  }
  
  
  public static class State {
    public boolean myIgnoreEmptyPrivateConstructors = true;
    public boolean myIgnoreImplicitConstructors = true;
  }

}
