// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@State(
  name = "JavaCoverageOptionsProvider",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
@Service(Service.Level.PROJECT)
public final class JavaCoverageOptionsProvider implements PersistentStateComponent<JavaCoverageOptionsProvider.State> {
  private final State myState = new State();
  private final Project myProject;

  public JavaCoverageOptionsProvider(Project project) {
    myProject = project;
  }

  public boolean ignoreImplicitConstructors() {
    return myState.myIgnoreImplicitConstructors;
  }

  public static JavaCoverageOptionsProvider getInstance(Project project) {
    return project.getService(JavaCoverageOptionsProvider.class);
  }

  public void setIgnoreImplicitConstructors(boolean state) {
    myState.myIgnoreImplicitConstructors = state;
  }

  void setExcludeAnnotationPatterns(List<String> patterns) {
    myState.myExcludeAnnotationPatterns = patterns;
  }

  public List<String> getExcludeAnnotationPatterns() {
    return myState.myExcludeAnnotationPatterns;
  }

  public boolean isGeneratedConstructor(String qualifiedName, String methodSignature) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (myState.myIgnoreImplicitConstructors) {
      PsiClass psiClass = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), qualifiedName));
      return PackageAnnotator.isGeneratedDefaultConstructor(psiClass, methodSignature);
    }
    return false;
  }

  @Override
  public JavaCoverageOptionsProvider.@NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull JavaCoverageOptionsProvider.State state) {
    myState.myIgnoreImplicitConstructors = state.myIgnoreImplicitConstructors;
    myState.myExcludeAnnotationPatterns = listWithDefaultAnnotations(state.myExcludeAnnotationPatterns);
  }

  @NotNull
  private static ArrayList<String> listWithDefaultAnnotations(List<String> patterns) {
    final LinkedHashSet<String> annotations = new LinkedHashSet<>(getDefaultExcludeAnnotationPatterns());
    annotations.addAll(patterns);
    return new ArrayList<>(annotations);
  }


  public static class State {
    public boolean myIgnoreImplicitConstructors = true;
    public List<String> myExcludeAnnotationPatterns = getDefaultExcludeAnnotationPatterns();
  }

  @NotNull
  public static List<String> getDefaultExcludeAnnotationPatterns() {
    return CollectionsKt.mutableListOf("*Generated*");
  }
}
