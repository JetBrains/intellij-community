// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public class DefaultBuildController implements BuildController {

  private final @NotNull BuildController myDelegate;

  private final @NotNull GradleBuild myMainGradleBuild;
  private final @NotNull Model myMyMainGradleBuildRootProject;

  private final @NotNull BuildEnvironment myBuildEnvironment;

  public DefaultBuildController(
    @NotNull BuildController buildController,
    @NotNull GradleBuild mainGradleBuild,
    @NotNull BuildEnvironment buildEnvironment
  ) {
    myDelegate = buildController;

    myMainGradleBuild = mainGradleBuild;
    myMyMainGradleBuildRootProject = myMainGradleBuild.getRootProject();

    myBuildEnvironment = buildEnvironment;
  }

  private boolean isMainBuild(Model model) {
    return model == null || model == myMainGradleBuild;
  }

  @Override
  public <T> T getModel(Class<T> aClass) throws UnknownModelException {
    // QD-10704
    //noinspection EqualsBetweenInconvertibleTypes
    if (aClass == GradleBuild.class) {
      //noinspection unchecked
      return (T)myMainGradleBuild;
    }
    return myDelegate.getModel(myMyMainGradleBuildRootProject, aClass);
  }

  @Override
  public <T> T findModel(Class<T> aClass) {
    // QD-10704
    //noinspection EqualsBetweenInconvertibleTypes
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

  /**
   * The old Gradle versions have deadlocks during parallel model fetch.
   * <p>
   * See <a href="https://github.com/gradle/gradle/issues/19837">Gradle issue 19837</a> for mode details.
   */
  private boolean isParallelModelFetchSupported() {
    String gradleVersion = myBuildEnvironment.getGradle().getGradleVersion();
    return GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.4.2");
  }

  @Override
  public void send(Object value) {
    myDelegate.send(value);
  }

  @Override
  public <T> List<T> run(Collection<? extends BuildAction<? extends T>> collection) {
    if (isParallelModelFetchSupported()) {
      return myDelegate.run(collection);
    }
    List<T> result = new ArrayList<>();
    for (BuildAction<? extends T> buildAction : collection) {
      result.add(buildAction.execute(this));
    }
    return result;
  }

  @Override
  public boolean getCanQueryProjectModelInParallel(Class<?> aClass) {
    return isParallelModelFetchSupported() && myDelegate.getCanQueryProjectModelInParallel(aClass);
  }
}
