package org.jetbrains.jps.android.model;

import org.jetbrains.android.util.AndroidNativeLibData;
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

  File getResourceDir() throws IOException;

  File getResourceDirForCompilation() throws IOException;

  File getManifestFile() throws IOException;

  File getManifestFileForCompilation() throws IOException;

  File getProguardConfigFile() throws IOException;

  File getAssetsDir() throws IOException;

  File getNativeLibsDir() throws IOException;

  boolean isLibrary();

  boolean useCustomResFolderForCompilation();

  boolean useCustomManifestForCompilation();

  boolean isPackTestCode();

  boolean isRunProcessResourcesMavenTask();

  boolean isRunProguard();

  boolean isIncludeSystemProguardCfgFile();

  String getApkRelativePath();

  String getBaseModulePath();

  String getCustomDebugKeyStorePath();

  List<AndroidNativeLibData> getAdditionalNativeLibs();
}
