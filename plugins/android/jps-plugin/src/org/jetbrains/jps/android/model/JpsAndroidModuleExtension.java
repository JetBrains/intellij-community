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
  File getResourceDir();

  @Nullable
  File getResourceDirForCompilation();

  @Nullable
  File getManifestFile();

  @Nullable
  File getManifestFileForCompilation();

  @Nullable
  File getProguardConfigFile() throws IOException;

  @Nullable
  File getAssetsDir();

  @Nullable
  File getAaptGenDir() throws IOException;

  @Nullable
  File getAidlGenDir() throws IOException;

  @Nullable
  File getNativeLibsDir();

  boolean isLibrary();

  boolean useCustomResFolderForCompilation();

  boolean useCustomManifestForCompilation();

  boolean isPackTestCode();

  boolean isIncludeAssetsFromLibraries();

  boolean isRunProcessResourcesMavenTask();

  boolean isRunProguard();

  boolean isIncludeSystemProguardCfgFile();

  String getApkRelativePath();

  String getCustomDebugKeyStorePath();

  List<AndroidNativeLibData> getAdditionalNativeLibs();

  boolean isUseCustomManifestPackage();

  String getCustomManifestPackage();
}
