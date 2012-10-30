package org.jetbrains.jps.appengine.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineEnhancerBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Collections.singletonList(new AppEngineEnhancerBuilder());
  }
}
