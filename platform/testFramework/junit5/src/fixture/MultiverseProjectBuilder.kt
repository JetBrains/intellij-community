// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class MultiverseProjectBuilder(val name: String) {
  private val directories: MutableList<DirectoryBuilder> = mutableListOf()
  private val modules: MutableList<ModuleBuilder> = mutableListOf()
  private val sourceRootRegistry: MutableMap<String, SourceRootBuilder> = mutableMapOf()
  private val contentRoots: MutableMap<String, MutableMap<String, ContentRootBuilder>> = mutableMapOf()

  fun getModules(): List<ModuleBuilder> = modules
  fun getContentRoot(name: String): Map<String, ContentRootBuilder>? = contentRoots[name]

  fun dir(name: String, init: DirectoryBuilder.() -> Unit) {
    val directory = DirectoryBuilder(name, path = name)
    directory.init()
    directories.add(directory)
  }

  fun module(moduleName: String, init: ModuleBuilder.() -> Unit) {
    val moduleBuilder = ModuleBuilder(moduleName, modulePath = moduleName)
    moduleBuilder.init()
    modules.add(moduleBuilder)
  }

  inner class ModuleBuilder(val moduleName: String, private val modulePath: String) : DirectoryContainer, ModuleContainer {
    private val directories: MutableList<DirectoryBuilder> = mutableListOf()
    private val nestedModules: MutableList<ModuleBuilder> = mutableListOf()

    override fun getDirectories(): List<DirectoryBuilder> = directories
    override fun getNestedModules(): List<ModuleBuilder> = nestedModules
    fun getPath(): String = modulePath

    fun contentRoot(name: String, init: ContentRootBuilder.() -> Unit) {
      val contentRootPath = "$modulePath/$name"
      val moduleMap = contentRoots.getOrPut(moduleName) { mutableMapOf() }
      val contentRootBuilder = moduleMap[name]
                               ?: ContentRootBuilder(name, contentRootPath).also { moduleMap[name] = it }
      contentRootBuilder.init()
    }

    fun sharedContentRoot(contentRootName: String, sourceModuleName: String, sharedSourceRootInit: ContentRootBuilder.() -> Unit = {}) {
      val sourceContentMap = contentRoots[sourceModuleName]
                             ?: throw IllegalArgumentException("Module '$sourceModuleName' has no content roots registered!")

      val sourceContentRoot = sourceContentMap[contentRootName]
                              ?: throw IllegalArgumentException("Content root '$contentRootName' does not exist in module '$sourceModuleName'.")

      val moduleMap = contentRoots.getOrPut(moduleName) { mutableMapOf() }
      moduleMap[contentRootName] = ContentRootBuilder(
        name = sourceContentRoot.name,
        path = sourceContentRoot.getContentRootPath(),
        isExisting = true
      ).apply(sharedSourceRootInit)
    }

    fun sharedSourceRoot(sourceRootId: String) {
      val sharedSourceRootBuilder = sourceRootRegistry[sourceRootId]
                                    ?: throw IllegalArgumentException("Source root ID '$sourceRootId' does not exist in the registry!")

      val contentRootName = sharedSourceRootBuilder.name
      val moduleMap = contentRoots.getOrPut(moduleName) { mutableMapOf() }
      val existingContentRoot = moduleMap[contentRootName]

      val contentRoot = existingContentRoot ?: ContentRootBuilder(
        name = contentRootName,
        path = sharedSourceRootBuilder.getSourceRootPath() ?: "",
        isExisting = true
      ).also { moduleMap[contentRootName] = it }

      contentRoot.addSourceRoot(sharedSourceRootBuilder)
    }

    override fun dir(name: String, init: DirectoryBuilder.() -> Unit) {
      val directoryPath = "$modulePath/$name"
      val directory = DirectoryBuilder(name, directoryPath)
      directory.init()
      directories.add(directory)
    }

    override fun module(moduleName: String, init: ModuleBuilder.() -> Unit) {
      val nestedModulePath = "$modulePath/$moduleName"
      val nestedModule = ModuleBuilder(moduleName, nestedModulePath).apply(init)
      nestedModules.add(nestedModule)
    }
  }

  inner class ContentRootBuilder(
    val name: String,
    private val path: String,
    val isExisting: Boolean = false,
  ) : DirectoryContainer, ModuleContainer {
    private val directories: MutableList<DirectoryBuilder> = mutableListOf()
    private val nestedModules: MutableList<ModuleBuilder> = mutableListOf()
    private val sourceRoots: MutableList<SourceRootBuilder> = mutableListOf()

    override fun getDirectories(): List<DirectoryBuilder> = directories
    override fun getNestedModules(): List<ModuleBuilder> = nestedModules
    fun getSourceRoots(): List<SourceRootBuilder> = sourceRoots
    fun addSourceRoot(sourceRoot: SourceRootBuilder) {
      if (sourceRoots.none { it.name == sourceRoot.name }) {
        sourceRoots.add(sourceRoot)
      }
    }

    fun sourceRoot(name: String, sourceRootId: String? = null, init: SourceRootBuilder.() -> Unit) {
      val sourceRootPath = "$path/$name"
      val sourceRootBuilder = SourceRootBuilder(name, sourceRootPath).apply(init)
      sourceRoots.add(sourceRootBuilder)

      sourceRootId?.let { id ->
        if (!sourceRootRegistry.containsKey(id)) {
          sourceRootRegistry[id] = sourceRootBuilder
        }
      }
    }

    fun sharedSourceRoot(sourceRootId: String) {
      val sharedSourceRootBuilder = sourceRootRegistry[sourceRootId]
                                    ?: throw IllegalArgumentException("Source root ID '$sourceRootId' does not exist in the registry!")

      if (sourceRoots.none { it.name == sharedSourceRootBuilder.name }) {
        sourceRoots.add(sharedSourceRootBuilder)
      }
    }

    override fun dir(name: String, init: DirectoryBuilder.() -> Unit) {
      val directoryPath = "$path/$name"
      val directory = DirectoryBuilder(name, directoryPath)
      directory.init()
      directories.add(directory)
    }

    override fun module(moduleName: String, init: ModuleBuilder.() -> Unit) {
      val nestedModulePath = "$path/$moduleName"
      val nestedModule = ModuleBuilder(moduleName, nestedModulePath).apply(init)
      nestedModules.add(nestedModule)
    }

    fun getContentRootPath(): String = path
  }

  inner class SourceRootBuilder(
    val name: String,
    private val path: String? = null,
    val isExisting: Boolean = false,
  ) : DirectoryContainer, ModuleContainer {
    private val directories: MutableList<DirectoryBuilder> = mutableListOf()
    private val nestedModules: MutableList<ModuleBuilder> = mutableListOf()
    private val files: MutableList<FileBuilder> = mutableListOf()

    override fun getDirectories(): List<DirectoryBuilder> = directories
    override fun getNestedModules(): List<ModuleBuilder> = nestedModules
    fun getFiles(): List<FileBuilder> = files

    fun file(fileName: String, content: String) {
      files.add(FileBuilder(fileName, content, "$path/$fileName"))
    }

    override fun dir(name: String, init: DirectoryBuilder.() -> Unit) {
      val directoryPath = "$path/$name"
      val directory = DirectoryBuilder(name, directoryPath)
      directory.init()
      directories.add(directory)
    }

    override fun module(moduleName: String, init: ModuleBuilder.() -> Unit) {
      val nestedModulePath = "$path/$moduleName"
      val nestedModule = ModuleBuilder(moduleName, nestedModulePath).apply(init)
      nestedModules.add(nestedModule)
    }

    fun getSourceRootPath(): String? = path
  }

  inner class DirectoryBuilder(val name: String, private val path: String) : DirectoryContainer, ModuleContainer {
    private val directories: MutableList<DirectoryBuilder> = mutableListOf()
    private val nestedModules: MutableList<ModuleBuilder> = mutableListOf()
    private val files: MutableList<FileBuilder> = mutableListOf()

    override fun getDirectories(): List<DirectoryBuilder> = directories
    override fun getNestedModules(): List<ModuleBuilder> = nestedModules
    fun getFiles(): List<FileBuilder> = files

    fun file(fileName: String, content: String) {
      files.add(FileBuilder(fileName, content, "$path/$fileName"))
    }

    override fun dir(name: String, init: DirectoryBuilder.() -> Unit) {
      val directoryPath = "$path/$name"
      val directory = DirectoryBuilder(name, directoryPath)
      directory.init()
      directories.add(directory)
    }

    override fun module(moduleName: String, init: ModuleBuilder.() -> Unit) {
      val nestedModulePath = "$path/$moduleName"
      val nestedModule = ModuleBuilder(moduleName, nestedModulePath).apply(init)
      nestedModules.add(nestedModule)
    }
  }

  inner class FileBuilder(private val name: String, private val content: String, private val path: String) {
    fun getFileName(): String = name
    fun getFilePath(): String = path
    fun getFileContent(): String = content
  }

  interface DirectoryContainer {
    fun getDirectories(): List<DirectoryBuilder>
    fun dir(name: String, init: DirectoryBuilder.() -> Unit)
  }

  interface ModuleContainer {
    fun getNestedModules(): List<ModuleBuilder>
    fun module(moduleName: String, init: ModuleBuilder.() -> Unit)
  }
}

private suspend fun getPsiDirectoryFromVfs(psiManager: PsiManager, directoryVfs: VirtualFile): PsiDirectory {
  return readAction {
    psiManager.findDirectory(directoryVfs)
    ?: error("Failed to find PsiDirectory for VirtualFile: $directoryVfs")
  }
}

private suspend fun createDirectory(targetPath: Path): VirtualFile {
  return edtWriteAction {
    VfsUtil.createDirectories(targetPath.toCanonicalPath())
    ?: error("Failed to create directory at path: $targetPath")
  }
}

private suspend fun deleteDirectorySafely(psiDirectory: PsiDirectory, targetPath: Path, project: Project) {
  edtWriteAction {
    if (!psiDirectory.isValid) {
      val resolvedVfsDirectory = LocalFileSystem.getInstance().findFileByPath(targetPath.toString())
      resolvedVfsDirectory?.delete(project)
    }
    else {
      psiDirectory.delete()
    }
  }
}

@TestOnly
private fun <T> TestFixture<T>.customDirectoryFixture(
  root: Path,
  dirName: String,
  getProject: (T) -> Project,
  onDirectoryCreated: (VirtualFile, T) -> Unit,
): TestFixture<PsiDirectory> = testFixture {
  val targetPath = root.resolve(dirName).toAbsolutePath()
  val parent = this@customDirectoryFixture.init()

  val parentPath = targetPath.parent
  if (parentPath != null && !Files.exists(parentPath)) {
    createDirectory(parentPath)
  }

  val directoryVfs = createDirectory(targetPath)

  onDirectoryCreated(directoryVfs, parent)

  val psiDirectory = getPsiDirectoryFromVfs(PsiManager.getInstance(getProject(parent)), directoryVfs)

  initialized(psiDirectory) {
    deleteDirectorySafely(psiDirectory, targetPath, getProject(parent))
  }
}

private val getProjectFromModule: (Module) -> Project = { it.project }
private val getProjectFromPsiDirectory: (PsiDirectory) -> Project = { it.project }

@TestOnly
private fun TestFixture<PsiDirectory>.customPsiDirectoryFixture(
  root: Path,
  dirName: String,
): TestFixture<PsiDirectory> = customDirectoryFixture(root, dirName, getProjectFromPsiDirectory) { _, _ ->
  // No additional actions are needed for PsiDirectory fixture.
}

@TestOnly
private fun TestFixture<Module>.customContentRootFixture(
  root: Path,
  dirName: String,
): TestFixture<PsiDirectory> = customDirectoryFixture(root, dirName, getProjectFromModule) { directoryVfs, module ->
  ModuleRootModificationUtil.updateModel(module) { model ->
    model.addContentEntry(directoryVfs)
  }
}

@TestOnly
private fun TestFixture<Module>.customSourceRootFixture(
  isTestSource: Boolean = false,
  contentEntry: Path,
  dirName: String,
): TestFixture<PsiDirectory> = customDirectoryFixture(contentEntry, dirName, getProjectFromModule) { directoryVfs, module ->
  ModuleRootModificationUtil.updateModel(module) { model ->
    val entry = model.contentEntries.find { it.file?.path == contentEntry.toAbsolutePath().toString() }
    if (entry == null) {
      println("Warning: Content entry with path '${contentEntry.toAbsolutePath()}' was not found in module ${module.name}.")
      return@updateModel
    }
    entry.addSourceFolder(directoryVfs, isTestSource)
  }
}

/**
 * Creates path fixture without generating a random name for directory inside.
 */
@TestOnly
private fun customPathFixture(root: Path): TestFixture<Path> = testFixture {
  val tempDir = withContext(Dispatchers.IO) {
    if (!root.exists()) {
      root.createDirectories()
    }
    root
  }
  initialized(tempDir) {
    withContext(Dispatchers.IO) {
      if (tempDir.exists()) {
        tempDir.delete(recursively = true)
      }
    }
  }
}

@TestOnly
fun multiverseProjectFixture(name: String, init: MultiverseProjectBuilder.() -> Unit): TestFixture<Project> {
  val builder = MultiverseProjectBuilder(name)
  builder.init()
  return buildMultiverseFixture(builder)
}

@TestOnly
private fun buildMultiverseFixture(builder: MultiverseProjectBuilder): TestFixture<Project> = testFixture(builder.name) {
  val initializer = MultiverseFixtureInitializer(builder)
  with(initializer) {
    initializeProjectModel()
  }

  initialized(initializer.project) {
    /*
     Nothing to dispose
     */
  }
}

@TestOnly
private class MultiverseFixtureInitializer(private val builder: MultiverseProjectBuilder) {
  lateinit var projectFixture: TestFixture<Project>
  lateinit var project: Project

  suspend fun TestFixtureInitializer.R<Project>.initializeProjectModel(): Project {
    this@MultiverseFixtureInitializer.projectFixture = projectFixture()
    this@MultiverseFixtureInitializer.project = projectFixture.init()

    val basePath = project.basePath?.let { Path.of(it) }
                   ?: throw IllegalStateException("Project base path is not available")

    builder.getModules().forEach { module ->
      processModule(basePath, module)
    }

    return project
  }

  private suspend fun TestFixtureInitializer.R<Project>.processModule(
    parentPath: Path,
    module: MultiverseProjectBuilder.ModuleBuilder,
  ) {
    val modulePath = parentPath.resolve(module.moduleName)
    val modulePathFixture = customPathFixture(modulePath)
    val moduleFixture = projectFixture.moduleFixture(modulePathFixture)
    moduleFixture.init()

    val contentRoots = builder.getContentRoot(module.moduleName)
                      ?: throw IllegalStateException("Content roots entity for module '${module.moduleName}' is null")

    contentRoots.values.forEach { contentRoot ->
      val contentRootPath = modulePath.resolve(contentRoot.name)
      val contentRootFixture = if (contentRoot.isExisting) {
        moduleFixture.customContentRootFixture(contentRootPath.parent, contentRoot.name)
      }
      else {
        moduleFixture.customContentRootFixture(modulePath, contentRoot.name)
      }
      contentRootFixture.init()

      initializeDirectoriesAndFiles(
        parentPath = contentRootPath,
        psiFixture = contentRootFixture,
        directories = contentRoot.getDirectories()
      )

      contentRoot.getSourceRoots().forEach { sourceRoot ->
        val sourceRootPath = contentRootPath.resolve(sourceRoot.name)
        val sourceRootFixture = moduleFixture.customSourceRootFixture(contentEntry = contentRootPath, dirName = sourceRoot.name)
        sourceRootFixture.init()

        if (!sourceRoot.isExisting) {
          initializeDirectoriesAndFiles(
            parentPath = sourceRootPath,
            psiFixture = sourceRootFixture,
            directories = sourceRoot.getDirectories()
          )

          sourceRoot.getNestedModules().forEach { nestedModule ->
            processModule(sourceRootPath, nestedModule)
          }
        }
      }

      contentRoot.getNestedModules().forEach { nestedModule ->
        processModule(contentRootPath, nestedModule)
      }
    }

    module.getNestedModules().forEach { nestedModule ->
      processModule(modulePath, nestedModule)
    }
  }

  private suspend fun TestFixtureInitializer.R<Project>.initializeDirectoriesAndFiles(
    parentPath: Path,
    psiFixture: TestFixture<PsiDirectory>,
    directories: List<MultiverseProjectBuilder.DirectoryBuilder>,
  ) {
    directories.forEach { directory ->
      val directoryFixture = psiFixture.customPsiDirectoryFixture(
        root = parentPath,
        dirName = directory.name
      )

      directoryFixture.init()

      directory.getFiles().forEach { file ->
        directoryFixture.psiFileFixture(file.getFileName(), file.getFileContent()).init()
      }

      val directoryPath = parentPath.resolve(directory.name)

      initializeDirectoriesAndFiles(
        parentPath = directoryPath,
        psiFixture = directoryFixture,
        directories = directory.getDirectories()
      )

      directory.getNestedModules().forEach { nestedModule ->
        processModule(directoryPath, nestedModule)
      }
    }
  }
}
