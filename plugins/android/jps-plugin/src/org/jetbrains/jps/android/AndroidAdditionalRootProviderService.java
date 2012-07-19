package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.AdditionalRootsProviderService;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAdditionalRootProviderService extends AdditionalRootsProviderService {

  @NotNull
  @Override
  public List<String> getAdditionalSourceRoots(@NotNull JpsModule module, BuildDataManager dataManager) {
    final File generatedSourcesRoot = AndroidJpsUtil.getGeneratedSourcesStorage(module, dataManager);
    final List<String> result = new ArrayList<String>();

    result.add(new File(generatedSourcesRoot, AndroidJpsUtil.AAPT_GENERATED_SOURCE_ROOT_NAME).getPath());
    result.add(new File(generatedSourcesRoot, AndroidJpsUtil.AIDL_GENERATED_SOURCE_ROOT_NAME).getPath());
    result.add(new File(generatedSourcesRoot, AndroidJpsUtil.RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME).getPath());
    result.add(new File(generatedSourcesRoot, AndroidJpsUtil.BUILD_CONFIG_GENERATED_SOURCE_ROOT_NAME).getPath());

    return result;
  }
}
