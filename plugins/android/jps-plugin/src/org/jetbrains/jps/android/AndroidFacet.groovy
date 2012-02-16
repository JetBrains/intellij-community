package org.jetbrains.jps.android

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet

/**
 * @author Eugene.Kudelevsky
 */
class AndroidFacet extends Facet {
  final Module module

  boolean library;

  String assetsFolderRelativePath;

  String resFolderRelativePath
  String resFolderForCompilationRelativePath
  boolean useCustomResFolderForCompilation;

  String manifestRelativePath;
  String manifestForCompilationRelativePath;
  boolean useCustomManifestForCompilation;
  boolean packTestCode;

  AndroidFacet(Module module, String name) {
    this.module = module
    this.name = name;
    this.resFolderRelativePath = resFolderRelativePath
  }

  File getResourceDir() throws IOException {
    def resDir = findFileByRelativeModulePath(resFolderRelativePath, true)
    return resDir != null ? resDir.getCanonicalFile() : null;
  }

  File getResourceDirForCompilation() throws IOException {
    def resDir = findFileByRelativeModulePath(resFolderForCompilationRelativePath, false)
    return resDir != null ? resDir.getCanonicalFile() : null;
  }

  File getManifestFile() throws IOException {
    def manifestFile = findFileByRelativeModulePath(manifestRelativePath, true);
    return manifestFile != null ? manifestFile.getCanonicalFile() : null;
  }

  File getManifestFileForCompilation() throws IOException {
    def manifestFile = findFileByRelativeModulePath(manifestForCompilationRelativePath, false);
    return manifestFile != null ? manifestFile.getCanonicalFile() : null;
  }

  File getAssetsDir() throws IOException {
    def manifestFile = findFileByRelativeModulePath(assetsFolderRelativePath, false);
    return manifestFile != null ? manifestFile.getCanonicalFile() : null;
  }

  private File findFileByRelativeModulePath(String relativePath, boolean lookInContentRoot) {
    if (module.basePath != null) {
      def absPath = FileUtil.toSystemIndependentName(module.basePath + relativePath)
      def f = new File(absPath)

      if (f.exists()) {
        return f
      }
    }

    if (lookInContentRoot) {
      module.contentRoots.each {String contentRoot ->
        def absPath = FileUtil.toSystemIndependentName(contentRoot + relativePath)
        def f = new File(absPath)

        if (f.exists()) {
          return f
        }
      }
    }
    return null;
  }
}
