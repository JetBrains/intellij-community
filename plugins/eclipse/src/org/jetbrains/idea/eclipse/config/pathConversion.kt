// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.http.HttpFileSystem
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.idea.eclipse.AbstractEclipseClasspathReader
import org.jetbrains.idea.eclipse.EPathCommonUtil
import org.jetbrains.idea.eclipse.EclipseXml
import org.jetbrains.idea.eclipse.conversion.EJavadocUtil
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File

private val LOG = logger<EclipseModuleRootsSerializer>()

internal fun convertToJavadocUrl(originalPath: String,
                                 moduleEntity: ModuleEntity,
                                 relativePathResolver: ModuleRelativePathResolver,
                                 virtualUrlManager: VirtualFileUrlManager): VirtualFileUrl {
  val javadocPath = if (!SystemInfo.isWindows) {
    originalPath.replaceFirst(EclipseXml.FILE_PROTOCOL, EclipseXml.FILE_PROTOCOL + "/")
  }
  else {
    originalPath
  }

  if (javadocPath.startsWith(EclipseXml.FILE_PROTOCOL)) {
    val path = javadocPath.removePrefix(EclipseXml.FILE_PROTOCOL)
    if (File(path).exists()) {
      return virtualUrlManager.fromUrl(pathToUrl(path))
    }
  }
  else {
    val protocol = VirtualFileManager.extractProtocol(javadocPath)
    if (protocol == HttpFileSystem.getInstance().protocol) {
      return virtualUrlManager.fromUrl(javadocPath)
    }
    else if (javadocPath.startsWith(EclipseXml.JAR_PREFIX)) {
      val jarJavadocPath = javadocPath.removePrefix(EclipseXml.JAR_PREFIX)
      if (jarJavadocPath.startsWith(EclipseXml.PLATFORM_PROTOCOL)) {
        val relativeToPlatform = jarJavadocPath.substring(EclipseXml.PLATFORM_PROTOCOL.length + "resource".length) // starts with leading /
        val currentRoot = moduleEntity.mainContentRoot?.url?.virtualFile
        val basePath = currentRoot?.parent?.path ?: JpsPathUtil.urlToPath(
          (moduleEntity.entitySource as EclipseProjectFile).projectLocation.baseDirectoryUrl.url)
        val currentModulePath = basePath + relativeToPlatform
        if (EJavadocUtil.isJarFileExist(currentModulePath)) {
          return virtualUrlManager.fromUrl(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, currentModulePath))
        }
        else {
          val moduleName = EPathCommonUtil.getRelativeModuleName(relativeToPlatform)
          val relativeToModulePathWithJarSuffix = EPathCommonUtil.getRelativeToModulePath(relativeToPlatform)
          val relativeToModulePath = EJavadocUtil.stripPathInsideJar(relativeToModulePathWithJarSuffix)
          var url: String? = null
          if (moduleName != moduleEntity.name) {
            url = relativePathResolver.resolve(moduleName, relativeToModulePath)
          }
          if (url != null) {
            assert(relativeToModulePathWithJarSuffix != null)
            assert(relativeToModulePath != null)
            if (relativeToModulePath!!.length < relativeToModulePathWithJarSuffix!!.length) {
              url += relativeToModulePathWithJarSuffix.substring(relativeToModulePath.length)
            }
            return virtualUrlManager.fromUrl(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, VfsUtil.urlToPath(url)))
          }
        }
      }
      else if (jarJavadocPath.startsWith(EclipseXml.FILE_PROTOCOL)) {
        val localFile = jarJavadocPath.substring(EclipseXml.FILE_PROTOCOL.length)
        if (EJavadocUtil.isJarFileExist(localFile)) {
          return virtualUrlManager.fromUrl(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, localFile))
        }
      }
    }
  }
  return virtualUrlManager.fromUrl(javadocPath)
}

internal fun convertToEclipseJavadocPath(javadocRoot: VirtualFileUrl,
                                         module: ModuleEntity,
                                         projectLocation: JpsProjectConfigLocation,
                                         pathShortener: ModulePathShortener): String? {
  val javadocUrl = javadocRoot.url
  val protocol = VirtualFileManager.extractProtocol(javadocUrl)
  if (protocol != HttpFileSystem.getInstance().protocol) {
    val path = VfsUtil.urlToPath(javadocUrl)
    val contentRoot = module.mainContentRoot?.url?.virtualFile
    val baseDir = if (contentRoot != null) contentRoot.parent else projectLocation.baseDirectoryUrl.virtualFile
    if (protocol == JarFileSystem.getInstance().protocol) {
      val javadocFile = JarFileSystem.getInstance().getVirtualFileForJar(javadocRoot.virtualFile)
      if (javadocFile != null) {
        val relativeUrl: String? = if (contentRoot != null && baseDir != null && VfsUtilCore.isAncestor(contentRoot, javadocFile, false)) {
          "/" + VfsUtilCore.getRelativePath(javadocFile, baseDir, '/')
        }
        else {
          pathShortener.shortenPath(javadocFile)
        }
        if (relativeUrl != null) {
          var javadocPath = javadocUrl
          if (!javadocPath.contains(JarFileSystem.JAR_SEPARATOR)) {
            javadocPath = javadocPath.removeSuffix("/") + JarFileSystem.JAR_SEPARATOR
          }
          return EclipseXml.JAR_PREFIX + EclipseXml.PLATFORM_PROTOCOL + "resource" + relativeUrl +
                 javadocPath.substring(javadocFile.url.length - 1)
        }
        else {
          return EclipseXml.JAR_PREFIX + EclipseXml.FILE_PROTOCOL + path.removePrefix("/")
        }
      }
      else {
        return EclipseXml.JAR_PREFIX + EclipseXml.FILE_PROTOCOL + path.removePrefix("/")
      }
    }
    else if (File(path).exists()) {
      return EclipseXml.FILE_PROTOCOL + path.removePrefix("/")
    }
  }
  return javadocUrl
}


internal fun convertToRootUrl(path: String, virtualUrlManager: VirtualFileUrlManager): VirtualFileUrl {
  val url = pathToUrl(path)
  val localFile = VirtualFileManager.getInstance().findFileByUrl(url)
  if (localFile != null) {
    val jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile)
    if (jarFile != null) {
      return virtualUrlManager.fromUrl(jarFile.url)
    }
  }

  return virtualUrlManager.fromUrl(url)
}

internal fun pathToUrl(path: String): String = JpsPathUtil.pathToUrl(FileUtil.toSystemIndependentName(path))

internal fun convertVariablePathToUrl(pathMap: ExpandMacroToPathMap,
                                      path: String?,
                                      varStart: Int,
                                      virtualUrlManager: VirtualFileUrlManager): VirtualFileUrl {
  val variable = AbstractEclipseClasspathReader.createEPathVariable(path, varStart)
  val pathWithVariable = AbstractEclipseClasspathReader.getVariableRelatedPath(variable.variable, variable.relatedPath)
  return convertToRootUrl(pathMap.substitute(pathWithVariable, SystemInfo.isFileSystemCaseSensitive), virtualUrlManager)
}

internal fun convertRelativePathToUrl(path: String,
                                      contentRootEntity: ContentRootEntity,
                                      pathResolver: ModuleRelativePathResolver,
                                      virtualUrlManager: VirtualFileUrlManager): VirtualFileUrl {
  if (!File(path).exists()) {
    if (path.startsWith("/")) {
      //relative to other project
      val moduleName = EPathCommonUtil.getRelativeModuleName(path)
      val relativeToRootPath = EPathCommonUtil.getRelativeToModulePath(path)
      if (moduleName != contentRootEntity.module.name) {
        val url = pathResolver.resolve(moduleName, relativeToRootPath)
        if (url != null) {
          return virtualUrlManager.fromUrl(url)
        }
      }
    }
    else {
      val url = findFileUnderContentRoot(path, contentRootEntity, virtualUrlManager)
      if (url != null) {
        return url
      }
    }
  }
  return convertToRootUrl(path, virtualUrlManager)
}

private fun findFileUnderContentRoot(path: String,
                                     contentRootEntity: ContentRootEntity,
                                     virtualUrlManager: VirtualFileUrlManager): VirtualFileUrl? {
  val root = contentRootEntity.url.toPath().toFile()
  val mainRoot = if (root.exists()) root
  else {
    val fileUrl = contentRootEntity.module.mainContentRoot?.url
    if (fileUrl != null) fileUrl.toPath().toFile() else null
  }
  val file = File(mainRoot, path)
  return if (file.exists()) convertToRootUrl(file.absolutePath, virtualUrlManager) else null
}

internal val ModuleEntity.mainContentRoot: ContentRootEntity?
  get() = contentRoots.firstOrNull {
    VirtualFileManager.getInstance().findFileByUrl(it.url.url)?.findChild(EclipseXml.PROJECT_FILE) != null
  }

internal fun getStorageRoot(imlFileUrl: VirtualFileUrl, customDir: String?, virtualFileManager: VirtualFileUrlManager): VirtualFileUrl {
  val moduleRoot = imlFileUrl.parent!!
  if (customDir == null) return moduleRoot
  if (OSAgnosticPathUtil.isAbsolute(customDir)) {
    return virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(customDir))
  }
  return moduleRoot.append(customDir)
}

internal fun convertToEclipsePath(fileUrl: VirtualFileUrl,
                                  moduleEntity: ModuleEntity,
                                  entitySource: EclipseProjectFile,
                                  pathShortener: ModulePathShortener): String? {
  val contentRoot = moduleEntity.mainContentRoot?.let { VirtualFileManager.getInstance().findFileByUrl(it.url.url) }
  var file = fileUrl.virtualFile
  val url = fileUrl.url
  if (file != null) {
    LOG.assertTrue(file.isValid)
    if (file.fileSystem is JarFileSystem) {
      val jarFile = JarFileSystem.getInstance().getVirtualFileForJar(file)
      if (jarFile == null) {
        LOG.error("Url: '$url'; file: $file")
        return ProjectRootManagerImpl.extractLocalPath(url)
      }
      file = jarFile
    }
    if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, file, false)) { //inside current project
      return VfsUtilCore.getRelativePath(file, contentRoot, '/')
    }
    else {
      val path = pathShortener.shortenPath(file)
      if (path != null) {
        return path
      }
    }
  }
  else { //try to avoid absolute path for deleted file
    if (contentRoot != null) {
      val rootUrl = contentRoot.url
      if (url.startsWith(rootUrl) && url.length > rootUrl.length) {
        return url.substring(rootUrl.length + 1) //without leading /
      }
    }
    val projectBaseDir = if (contentRoot != null) contentRoot.parent else entitySource.projectLocation.baseDirectoryUrl.virtualFile!!
    val projectUrl = projectBaseDir.url
    if (url.startsWith(projectUrl)) {
      return url.substring(projectUrl.length) //leading /
    }
    val path = VfsUtilCore.urlToPath(url)
    val projectPath = projectBaseDir.path
    if (path.startsWith(projectPath)) {
      return ProjectRootManagerImpl.extractLocalPath(path.substring(projectPath.length))
    }
  }
  return ProjectRootManagerImpl.extractLocalPath(url) //absolute path
}

fun convertToEclipsePathWithVariable(roots: List<LibraryRoot>): String? {
  roots.forEach { root ->
    var filePath = VirtualFileManager.extractPath(root.url.url)
    val jarSeparatorIdx = filePath.indexOf(JarFileSystem.JAR_SEPARATOR)
    if (jarSeparatorIdx > -1) {
      filePath = filePath.substring(0, jarSeparatorIdx)
    }
    val pathMacros = PathMacros.getInstance().userMacros
    for (name in pathMacros.keys) {
      val path = FileUtil.toSystemIndependentName(pathMacros[name]!!)
      if (filePath.startsWith("$path/")) {
        val substr = filePath.substring(path.length)
        return name + if (substr.startsWith("/") || substr.isEmpty()) substr else "/$substr"
      }
    }
  }
  return null
}
