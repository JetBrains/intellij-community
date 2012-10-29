package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.builder.AndroidBuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuilderService extends BuilderService {
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Arrays.asList(AndroidBuildTarget.TargetType.DEX, AndroidBuildTarget.TargetType.PACKAGING);
  }

  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new AndroidSourceGeneratingBuilder(),
                         new AndroidLibraryPackagingBuilder());
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?,?>> createBuilders() {
    return Arrays.asList(new AndroidDexBuilder(),
                         new AndroidPackagingBuilder());
  }
}
