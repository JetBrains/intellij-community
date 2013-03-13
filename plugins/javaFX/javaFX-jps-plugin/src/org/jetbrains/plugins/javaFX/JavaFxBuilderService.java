package org.jetbrains.plugins.javaFX;

import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;

import java.util.Arrays;
import java.util.List;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JavaFxBuilderService extends BuilderService {
  @Override
    public List<? extends BuildTargetType<?>> getTargetTypes() {
      return Arrays.<BuildTargetType<?>>asList(ArtifactBuildTargetType.INSTANCE);
    }
}
