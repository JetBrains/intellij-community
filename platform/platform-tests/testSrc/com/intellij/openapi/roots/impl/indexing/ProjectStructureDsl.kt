// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.io.File
import java.nio.file.Path

@DslMarker
annotation class ProjectStructureDsl

@ProjectStructureDsl
interface DirectoryContentBuilder {
  fun dir(name: String, content: DirectoryContentBuilder.() -> Unit): DirectorySpec
  fun file(name: String, content: String): FileSpec
  fun symlink(name: String, target: ContentSpec): SymlinkSpec
}

@ProjectStructureDsl
interface ModuleContentBuilder : DirectoryContentBuilder {
  fun content(name: String, content: ModuleContentBuilder.() -> Unit): ModuleRootSpec
  fun source(name: String, sourceRootType: JpsModuleSourceRootType<*>, content: ModuleContentBuilder.() -> Unit): ModuleRootSpec
  fun excluded(name: String, content: ModuleContentBuilder.() -> Unit): ModuleRootSpec

  /** Similar to [dir] but preserves [ModuleContentBuilder] receiver of the builder. */
  fun moduleDir(name: String, content: ModuleContentBuilder.() -> Unit): DirectorySpec
}

fun ModuleContentBuilder.source(name: String, content: ModuleContentBuilder.() -> Unit) =
  source(name, JavaSourceRootType.SOURCE, content)

fun ModuleContentBuilder.testSourceRoot(name: String, content: ModuleContentBuilder.() -> Unit) =
  source(name, JavaSourceRootType.TEST_SOURCE, content)

fun ModuleContentBuilder.resourceRoot(name: String, content: ModuleContentBuilder.() -> Unit) =
  source(name, JavaResourceRootType.RESOURCE, content)

fun ModuleContentBuilder.testResourceRoot(name: String, content: ModuleContentBuilder.() -> Unit) =
  source(name, JavaResourceRootType.TEST_RESOURCE, content)

interface ContentSpecPath {
  companion object {
    const val SEPARATOR = '/'
  }

  val rootDirectory: VirtualFile

  val path: String

  val parentPath: ContentSpecPath

  fun resolve(child: String): ContentSpecPath
}

infix operator fun ContentSpecPath.div(child: String) = resolve(child)

private class ContentSpecPathImpl(override val rootDirectory: VirtualFile, override val path: String) : ContentSpecPath {
  override fun resolve(child: String) = ContentSpecPathImpl(
    rootDirectory,
    if (path.isEmpty()) child else "$path${ContentSpecPath.SEPARATOR}$child"
  )

  override val parentPath: ContentSpecPath
    get() {
      check(path.isNotEmpty()) { "This path is already the root path $rootDirectory" }
      return ContentSpecPathImpl(rootDirectory, path.substringBeforeLast(ContentSpecPath.SEPARATOR, ""))
    }
}

interface ContentSpec {
  val specPath: ContentSpecPath

  fun generateTo(target: VirtualFile)

  fun generate(parentDirectory: VirtualFile, name: String): VirtualFile
}

fun ContentSpec.resolveVirtualFile(): VirtualFile {
  val file = specPath.rootDirectory.findFileByRelativePath(specPath.path.replace(ContentSpecPath.SEPARATOR, '/'))
  checkNotNull(file) { "Content spec has not been created yet: ${specPath.path} against ${specPath.rootDirectory}" }
  file.refresh(false, true)
  return file
}

abstract class ContentWithChildrenSpec(override val specPath: ContentSpecPath) : ContentSpec {
  private val children = linkedMapOf<String, ContentSpec>()

  fun addChild(name: String, spec: ContentSpec) {
    check(!FileUtil.toSystemIndependentName(name).contains('/')) { "only simple child names are allowed: $name" }
    check(name !in children) { "'$name' was already added" }
    children[name] = spec
  }

  override fun generateTo(target: VirtualFile) {
    for ((childName, spec) in children) {
      spec.generate(target, childName)
    }
  }

  override fun generate(parentDirectory: VirtualFile, name: String): VirtualFile {
    val thisDirectory = runWriteAction { parentDirectory.createChildDirectory(null, name) }
    generateTo(thisDirectory)
    return thisDirectory
  }
}

abstract class ModuleRootSpec(specPath: ContentSpecPath, protected val module: Module) : DirectorySpec(specPath)

class ContentRootSpec(
  specPath: ContentSpecPath,
  module: Module
) : ModuleRootSpec(specPath, module) {
  override fun generateTo(target: VirtualFile) {
    PsiTestUtil.addContentRoot(module, target)
    super.generateTo(target)
  }
}

class SourceRootSpec(
  specPath: ContentSpecPath,
  module: Module,
  private val sourceRootType: JpsModuleSourceRootType<*>
) : ModuleRootSpec(specPath, module) {
  override fun generateTo(target: VirtualFile) {
    PsiTestUtil.addSourceRoot(module, target, sourceRootType)
    super.generateTo(target)
  }
}

class ExcludedRootSpec(
  specPath: ContentSpecPath,
  module: Module
) : ModuleRootSpec(specPath, module) {
  override fun generateTo(target: VirtualFile) {
    PsiTestUtil.addExcludedRoot(module, target)
    super.generateTo(target)
  }
}

open class DirectorySpec(specPath: ContentSpecPath) : ContentWithChildrenSpec(specPath)

class FileSpec(override val specPath: ContentSpecPath, private val content: ByteArray) : ContentSpec {
  override fun generateTo(target: VirtualFile) {
    runWriteAction { target.getOutputStream(null).buffered().use { it.write(content) } }
  }

  override fun generate(parentDirectory: VirtualFile, name: String): VirtualFile {
    val file = runWriteAction { parentDirectory.createChildData(null, name) }
    generateTo(file)
    return file
  }
}

class SymlinkSpec(override val specPath: ContentSpecPath, private val target: ContentSpec) : ContentSpec {
  override fun generateTo(target: VirtualFile) {
    throw UnsupportedOperationException("Cannot override symlink file $target")
  }

  private fun ContentSpecPath.getAbsolutePath(): String =
    rootDirectory.path.trimEnd('/').replace('/', ContentSpecPath.SEPARATOR) +
    (if (path.isEmpty()) "" else ContentSpecPath.SEPARATOR + path)

  override fun generate(parentDirectory: VirtualFile, name: String): VirtualFile {
    val targetRelativePathToSymlink = FileUtil.getRelativePath(
      specPath.parentPath.getAbsolutePath(),
      target.specPath.getAbsolutePath(),
      ContentSpecPath.SEPARATOR
    )
    checkNotNull(targetRelativePathToSymlink) { "Path to symlink's target ${target.specPath.path} is not relative to ${specPath.path}" }
    val fullLinkPath = File(parentDirectory.path + '/' + name)
    check(!fullLinkPath.exists()) { "Link already exists: $fullLinkPath" }
    check(fullLinkPath.isAbsolute) { "Path to symlink must be absolute: $fullLinkPath" }
    val symlinkIoFile = IoTestUtil.createSymLink(targetRelativePathToSymlink, fullLinkPath.absolutePath)
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(symlinkIoFile))
  }
}

class DirectoryContentBuilderImpl(
  private val result: ContentWithChildrenSpec
) : DirectoryContentBuilder {

  override fun dir(name: String, content: DirectoryContentBuilder.() -> Unit): DirectorySpec {
    val directorySpec = DirectorySpec(result.specPath / name)
    DirectoryContentBuilderImpl(directorySpec).content()
    result.addChild(name, directorySpec)
    return directorySpec
  }

  override fun file(name: String, content: String): FileSpec {
    val spec = FileSpec(result.specPath / name, content.toByteArray())
    result.addChild(name, spec)
    return spec
  }

  override fun symlink(name: String, target: ContentSpec): SymlinkSpec {
    val spec = SymlinkSpec(result.specPath / name, target)
    result.addChild(name, spec)
    return spec
  }
}

class ModuleContentBuilderImpl(
  private val module: Module,
  private val result: ContentWithChildrenSpec
) : ModuleContentBuilder {

  private fun <S : ModuleRootSpec> addModuleContentChildSpec(name: String, spec: S, content: ModuleContentBuilder.() -> Unit): S {
    ModuleContentBuilderImpl(module, spec).content()
    result.addChild(name, spec)
    return spec
  }

  override fun content(name: String, content: ModuleContentBuilder.() -> Unit) =
    addModuleContentChildSpec(
      name,
      ContentRootSpec(result.specPath / name, module),
      content
    )

  override fun source(name: String, sourceRootType: JpsModuleSourceRootType<*>, content: ModuleContentBuilder.() -> Unit) =
    addModuleContentChildSpec(
      name,
      SourceRootSpec(result.specPath / name, module, sourceRootType),
      content
    )

  override fun excluded(name: String, content: ModuleContentBuilder.() -> Unit) =
    addModuleContentChildSpec(
      name,
      ExcludedRootSpec(result.specPath / name, module),
      content
    )

  override fun dir(name: String, content: DirectoryContentBuilder.() -> Unit) =
    DirectoryContentBuilderImpl(result).dir(name, content)

  override fun moduleDir(name: String, content: ModuleContentBuilder.() -> Unit): DirectorySpec {
    val directorySpec = DirectorySpec(result.specPath / name)
    ModuleContentBuilderImpl(module, directorySpec).content()
    result.addChild(name, directorySpec)
    return directorySpec
  }

  override fun file(name: String, content: String) =
    DirectoryContentBuilderImpl(result).file(name, content)

  override fun symlink(name: String, target: ContentSpec) =
    DirectoryContentBuilderImpl(result).symlink(name, target)
}

fun buildDirectoryContent(
  directory: VirtualFile,
  content: DirectoryContentBuilder.() -> Unit
) {
  val directoryPath = ContentSpecPathImpl(directory, "")
  val directorySpec = DirectorySpec(directoryPath)
  DirectoryContentBuilderImpl(directorySpec).content()
  directorySpec.generateTo(directory)
}

fun ProjectModelRule.createJavaModule(moduleName: String, content: ModuleContentBuilder.() -> Unit): Module {
  val moduleRoot = baseProjectDir.newVirtualDirectory(moduleName)
  val module = createJavaModule(project, moduleName, moduleRoot.toNioPath())
  val rootPath = ContentSpecPathImpl(moduleRoot, "")
  val directorySpec = DirectorySpec(rootPath)
  ModuleContentBuilderImpl(module, directorySpec).content()
  directorySpec.generateTo(moduleRoot)
  return module
}

private fun createJavaModule(project: Project, moduleName: String, moduleRootDirectory: Path): Module {
  val type = ModuleTypeManager.getInstance().findByID(ModuleTypeId.JAVA_MODULE)
  return WriteCommandAction.writeCommandAction(project).compute(
    ThrowableComputable<Module, RuntimeException> {
      val moduleModel = ModuleManager.getInstance(project).modifiableModel
      val module = moduleModel.newModule(moduleRootDirectory.resolve("$moduleName.iml"), type.id)
      moduleModel.commit()
      module
    }
  )
}