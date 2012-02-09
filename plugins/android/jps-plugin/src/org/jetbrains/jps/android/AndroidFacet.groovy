package org.jetbrains.jps.android

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet

/**
 * @author nik
 */
class AndroidFacet extends Facet {
  final Module module
  final String resFolderRelativePath

  AndroidFacet(Module module, String name, String resFolderRelativePath) {
    this.module = module
    this.name = name;
    this.resFolderRelativePath = resFolderRelativePath
  }

  File getResourceDir() {
    return findFileByRelativeModulePath(resFolderRelativePath, true)
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
      module.contentRoots.each {
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
