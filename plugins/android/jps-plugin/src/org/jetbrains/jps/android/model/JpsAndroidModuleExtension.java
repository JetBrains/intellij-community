package org.jetbrains.jps.android.model;

import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface JpsAndroidModuleExtension extends JpsElement {
  JpsModule getModule();

  @Nullable
  File getResourceDir() throws IOException;

  @Nullable
  File getResourceDirForCompilation() throws IOException;

  @Nullable
  File getManifestFile() throws IOException;

  @Nullable
  File getManifestFileForCompilation() throws IOException;

  @Nullable
  File getProguardConfigFile() throws IOException;

  @Nullable
  File getAssetsDir() throws IOException;

  @Nullable
  File getNativeLibsDir() throws IOException;

  boolean isLibrary();

  boolean useCustomResFolderForCompilation();

  boolean useCustomManifestForCompilation();

  boolean isPackTestCode();

  boolean isPackAssetsFromLibraries();

  boolean isRunProcessResourcesMavenTask();

  boolean isRunProguard();

  boolean isIncludeSystemProguardCfgFile();

  String getApkRelativePath();

  String getBaseModulePath();

  String getCustomDebugKeyStorePath();

  List<AndroidNativeLibData> getAdditionalNativeLibs();
}
