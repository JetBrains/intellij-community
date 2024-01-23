// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public class DefaultBuildController implements BuildController {

  private final BuildController myDelegate;
  private final GradleBuild myMainGradleBuild;
  private final Model myMyMainGradleBuildRootProject;

  public DefaultBuildController(@NotNull BuildController buildController, @NotNull GradleBuild mainGradleBuild) {
    myDelegate = buildController;
    myMainGradleBuild = mainGradleBuild;
    myMyMainGradleBuildRootProject = myMainGradleBuild.getRootProject();
  }

  @Override
  public <T> T getModel(Class<T> aClass) throws UnknownModelException {
    if (aClass == GradleBuild.class) {
      //noinspection unchecked
      return (T)myMainGradleBuild;
    }
    return myDelegate.getModel(myMyMainGradleBuildRootProject, aClass);
  }

  @Override
  public <T> T findModel(Class<T> aClass) {
    if (aClass == GradleBuild.class) {
      //noinspection unchecked
      return (T)myMainGradleBuild;
    }
    return myDelegate.findModel(myMyMainGradleBuildRootProject, aClass);
  }

  @Override
  public GradleBuild getBuildModel() {
    return myMainGradleBuild;
  }

  @Override
  public <T> T getModel(Model model, Class<T> aClass) throws UnknownModelException {
    if (isMainBuild(model)) {
      return getModel(aClass);
    }
    else {
      return myDelegate.getModel(model, aClass);
    }
  }

  @Override
  public <T> T findModel(Model model, Class<T> aClass) {
    if (isMainBuild(model)) {
      return findModel(aClass);
    }
    else {
      return myDelegate.findModel(model, aClass);
    }
  }

  @Override
  public <T, P> T getModel(Class<T> aClass, Class<P> aClass1, Action<? super P> action)
    throws UnsupportedVersionException {
    return myDelegate.getModel(myMyMainGradleBuildRootProject, aClass, aClass1, action);
  }

  @Override
  public <T, P> T findModel(Class<T> aClass, Class<P> aClass1, Action<? super P> action) {
    return myDelegate.findModel(myMyMainGradleBuildRootProject, aClass, aClass1, action);
  }

  @Override
  public <T, P> T getModel(Model model, Class<T> aClass, Class<P> aClass1, Action<? super P> action)
    throws UnsupportedVersionException {
    if (isMainBuild(model)) {
      return getModel(aClass, aClass1, action);
    }
    else {
      return myDelegate.getModel(model, aClass, aClass1, action);
    }
  }

  @Override
  public <T, P> T findModel(Model model, Class<T> aClass, Class<P> aClass1, Action<? super P> action) {
    if (isMainBuild(model)) {
      return findModel(aClass, aClass1, action);
    }
    else {
      return myDelegate.findModel(model, aClass, aClass1, action);
    }
  }

  @Override
  public <T> List<T> run(Collection<? extends BuildAction<? extends T>> collection) {
    return myDelegate.run(collection);
  }

  @Override
  public boolean getCanQueryProjectModelInParallel(Class<?> aClass) {
    return myDelegate.getCanQueryProjectModelInParallel(aClass);
  }

  private boolean isMainBuild(Model model) {
    return model == null || model == myMainGradleBuild;
  }
}
