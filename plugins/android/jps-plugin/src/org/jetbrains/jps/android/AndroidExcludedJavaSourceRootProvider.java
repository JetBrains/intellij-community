package org.jetbrains.jps.android;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExcludedJavaSourceRootProvider extends ExcludedJavaSourceRootProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidExcludedJavaSourceRootProvider");

  @Override
  public boolean isExcludedFromCompilation(@NotNull JpsModule module, @NotNull JpsModuleSourceRoot root) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

    if (extension == null) {
      return false;
    }
    try {
      for (String genDir : AndroidJpsUtil.getGenDirs(extension)) {
        if (FileUtil.pathsEqual(genDir, root.getFile().getPath())) {
          return true;
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return false;
  }
}
