package org.jetbrains.jps.android;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.idea.AdditionalRootsProviderService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAdditionalRootProviderService extends AdditionalRootsProviderService {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidAdditionalRootProviderService");

  @NotNull
  @Override
  public List<String> getAdditionalSourceRoots(@NotNull Module module) {
    final File generatedSourcesRoot = AndroidJpsUtil.getGeneratedSourcesStorage(module);
    final List<String> result = new ArrayList<String>();

    result.add(createSubdir(generatedSourcesRoot, AndroidJpsUtil.AAPT_GENERATED_SOURCE_ROOT_NAME));
    result.add(createSubdir(generatedSourcesRoot, AndroidJpsUtil.AIDL_GENERATED_SOURCE_ROOT_NAME));
    result.add(createSubdir(generatedSourcesRoot, AndroidJpsUtil.RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME));

    return result;
  }

  @NotNull
  private static String createSubdir(@NotNull File dir, @NotNull String name) {
    final File aaptSourceRoot = new File(dir, name);
    if (!aaptSourceRoot.exists() && !aaptSourceRoot.mkdirs()) {
      LOG.info("Cannot create folder " + aaptSourceRoot.getPath());
    }
    return aaptSourceRoot.getPath();
  }
}
