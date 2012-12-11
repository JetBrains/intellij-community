package org.jetbrains.jps.android.builder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public abstract class AndroidBuildTargetType<T extends AndroidBuildTarget> extends BuildTargetType<T> {

  private final String myPresentableName;

  AndroidBuildTargetType(@NotNull String typeId, @NotNull String presentableName) {
    super(typeId);
    myPresentableName = presentableName;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  @Override
  public List<T> computeAllTargets(@NotNull JpsModel model) {
    if (!AndroidJpsUtil.containsAndroidFacet(model.getProject())) {
      return Collections.emptyList();
    }
    final List<T> targets = new ArrayList<T>();

    for (JpsModule module : model.getProject().getModules()) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

      if (extension != null) {
        targets.add(createBuildTarget(module));
      }
    }
    return targets;
  }

  public abstract T createBuildTarget(@NotNull JpsModule module);

  @NotNull
  @Override
  public BuildTargetLoader<T> createLoader(@NotNull final JpsModel model) {
    final HashMap<String, T> targetMap = new HashMap<String, T>();

    for (T target : computeAllTargets(model)) {
      targetMap.put(target.getId(), target);
    }
    return new BuildTargetLoader<T>() {
      @Nullable
      @Override
      public T createTarget(@NotNull String targetId) {
        return targetMap.get(targetId);
      }
    };
  }
}
