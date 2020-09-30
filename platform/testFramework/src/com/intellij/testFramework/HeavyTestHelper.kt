// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@ApiStatus.Internal
internal object HeavyTestHelper {
  // not in HeavyIdeaTestFixtureImpl because of java
  @JvmStatic
  fun openHeavyTestFixtureProject(path: Path, moduleListener: ModuleListener): Project {
    val options = createTestOpenProjectOptions().copy(beforeOpen = {
      it.messageBus.simpleConnect().subscribe(ProjectTopics.MODULES, moduleListener)
      true
    })
    val project = ProjectManagerEx.getInstanceEx().openProject(path, options)!!
    if (ApplicationManager.getApplication().isDispatchThread) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    return project
  }

  // not in HeavyIdeaTestFixtureImpl to avoid IOException in API
  @JvmStatic
  fun createModuleAt(moduleName: String, project: Project, moduleType: ModuleType<*>, path: Path): Module {
    val moduleFile = path.resolve("$moduleName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
    val moduleManager = ModuleManager.getInstance(project)
    return WriteAction.computeAndWait<Module, Throwable> {
      moduleManager.newModule(moduleFile, moduleType.id)
    }
  }

  @JvmStatic
  fun createTestProjectStructure(module: Module?,
                                 rootPath: String?,
                                 dir: Path,
                                 addProjectRoots: Boolean): VirtualFile {
    val virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
                     ?: throw IllegalStateException("Cannot find virtual directory by $dir")
    virtualDir.getChildren()
    virtualDir.refresh(false, true)
    WriteAction.computeAndWait<Unit, IOException> {
      if (rootPath != null) {
        val vDir1 = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'))
                    ?: throw Exception("$rootPath not found")
        VfsUtil.copyDirectory(null, vDir1, virtualDir, null)
      }
      if (addProjectRoots) {
        ModuleRootModificationUtil.modifyModel(module!!) { model ->
          model.addContentEntry(virtualDir).addSourceFolder(virtualDir, false)
          true
        }
      }
    }
    return virtualDir
  }

  @JvmStatic
  fun createTempDirectoryForTempDirTestFixture(dir: Path?, prefix: String): Path {
    val parentDir = dir ?: Paths.get(FileUtil.getTempDirectory())
    Files.createDirectories(parentDir)
    return Files.createTempDirectory(parentDir, prefix)
  }

  @JvmStatic
  fun getOrCreateProjectBaseDir(project: Project): VirtualFile {
    val basePath = project.stateStore.getProjectBasePath()
    val fs = LocalFileSystem.getInstance()
    val baseDir = fs.findFileByNioFile(basePath)
    if (baseDir == null) {
      Files.createDirectories(basePath)
      return fs.refreshAndFindFileByNioFile(basePath)!!
    }
    return baseDir
  }

  @JvmStatic
  fun createChildDirectory(dir: VirtualFile, name: String): VirtualFile {
    return WriteAction.computeAndWait<VirtualFile, IOException> { dir.createChildDirectory(null, name) }
  }

  @JvmStatic
  fun createVirtualFileWithEncodingUsingNio(ext: String,
                                            bom: ByteArray?,
                                            content: String,
                                            charset: Charset,
                                            temporaryDirectory: TemporaryDirectory): VirtualFile {
    val file = temporaryDirectory.newPath(".$ext")
    Files.createDirectories(file.parent)
    Files.newByteChannel(file, HashSet(listOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))).use { channel ->
      if (bom != null) {
        channel.write(ByteBuffer.wrap(bom))
      }
      channel.write(charset.encode(CharBuffer.wrap(content)))
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
  }
}