package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.workspace.api.*
import com.intellij.util.ArrayUtilRt
import java.util.*

class LegacyBridgeCompilerModuleExtension(
  private val module: LegacyBridgeModule,
  private val entityStore: TypedEntityStore,
  private val diff: TypedEntityStorageDiffBuilder?
) : CompilerModuleExtension() {

  private var changed = false

  private val javaSettingsValue: CachedValue<JavaModuleSettingsEntity?> = CachedValue {
    it.resolve(module.moduleEntityId)?.javaSettings
  }

  private val javaSettings
    get() = entityStore.cachedValue(javaSettingsValue)

  private fun getSanitizedModuleName(): String {
    val file = module.moduleFile
    return file?.nameWithoutExtension ?: module.name
  }

  private fun getCompilerOutput(): VirtualFileUrl? {
    val javaSettings = javaSettings

    if (isCompilerOutputPathInherited) {
      val projectOutputUrl = CompilerProjectExtension.getInstance(module.project)?.compilerOutputUrl ?: return null
      return VirtualFileUrlManager.fromUrl(projectOutputUrl + "/" + PRODUCTION + "/" + getSanitizedModuleName())
    }

    return javaSettings?.compilerOutput
  }

  private fun getCompilerOutputForTests(): VirtualFileUrl? {
    val javaSettings = javaSettings

    if (isCompilerOutputPathInherited) {
      val projectOutputUrl = CompilerProjectExtension.getInstance(module.project)?.compilerOutputUrl ?: return null
      return VirtualFileUrlManager.fromUrl(projectOutputUrl + "/" + TEST + "/" + getSanitizedModuleName())
    }

    return javaSettings?.compilerOutputForTests
  }

  override fun isExcludeOutput(): Boolean = javaSettings?.excludeOutput ?: true
  override fun isCompilerOutputPathInherited(): Boolean = javaSettings?.inheritedCompilerOutput ?: true

  override fun getCompilerOutputUrl(): String? = getCompilerOutput()?.url
  override fun getCompilerOutputPath(): VirtualFile? = getCompilerOutput()?.virtualFile
  override fun getCompilerOutputPointer(): VirtualFilePointer? = getCompilerOutput()?.let {
    LegacyBridgeFilePointerProvider.getInstance(module).getAndCacheFilePointer(it)
  }

  override fun getCompilerOutputUrlForTests(): String? = getCompilerOutputForTests()?.url
  override fun getCompilerOutputPathForTests(): VirtualFile? = getCompilerOutputForTests()?.virtualFile
  override fun getCompilerOutputForTestsPointer(): VirtualFilePointer? = getCompilerOutputForTests()?.let {
    LegacyBridgeFilePointerProvider.getInstance(module).getAndCacheFilePointer(it)
  }

  override fun getModifiableModel(writable: Boolean): ModuleExtension = throw UnsupportedOperationException()

  override fun commit() = Unit
  override fun isChanged(): Boolean = changed
  override fun dispose() = Unit

  private fun updateJavaSettings(updater: ModifiableJavaModuleSettingsEntity.() -> Unit) {
    if (diff == null) {
      error("Read-only $javaClass")
    }

    val moduleEntity = entityStore.current.resolve(module.moduleEntityId) ?: error("Could not resolve ${module.moduleEntityId}")
    val moduleSource = moduleEntity.entitySource

    val oldJavaSettings = javaSettings ?: diff.addJavaModuleSettingsEntity(
      inheritedCompilerOutput = true,
      excludeOutput = true,
      compilerOutputForTests = null,
      compilerOutput = null,
      module = moduleEntity,
      source = moduleSource
    )

    diff.modifyEntity(ModifiableJavaModuleSettingsEntity::class.java, oldJavaSettings, updater)
    changed = true
  }

  override fun setCompilerOutputPath(file: VirtualFile?) {
    if (compilerOutputPath == file) return
    updateJavaSettings { compilerOutput = file?.virtualFileUrl }
  }

  override fun setCompilerOutputPath(url: String?) {
    if (compilerOutputUrl == url) return
    updateJavaSettings { compilerOutput = url?.let { VirtualFileUrlManager.fromUrl(it) } }
  }

  override fun setCompilerOutputPathForTests(file: VirtualFile?) {
    if (compilerOutputPathForTests == file) return
    updateJavaSettings { compilerOutputForTests = file?.virtualFileUrl }
  }

  override fun setCompilerOutputPathForTests(url: String?) {
    if (compilerOutputUrlForTests == url) return
    updateJavaSettings { compilerOutputForTests = url?.let { VirtualFileUrlManager.fromUrl(it) } }
  }

  override fun inheritCompilerOutputPath(inherit: Boolean) {
    if (isCompilerOutputPathInherited == inherit) return
    updateJavaSettings { inheritedCompilerOutput = inherit }
  }

  override fun setExcludeOutput(exclude: Boolean) {
    if (isExcludeOutput == exclude) return
    updateJavaSettings { excludeOutput = exclude }
  }

  override fun getOutputRoots(includeTests: Boolean): Array<VirtualFile> {
    val result = ArrayList<VirtualFile>()

    val outputPathForTests = if (includeTests) compilerOutputPathForTests else null
    if (outputPathForTests != null) {
      result.add(outputPathForTests)
    }

    val outputRoot = compilerOutputPath
    if (outputRoot != null && outputRoot != outputPathForTests) {
      result.add(outputRoot)
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  override fun getOutputRootUrls(includeTests: Boolean): Array<String> {
    val result = ArrayList<String>()

    val outputPathForTests = if (includeTests) compilerOutputUrlForTests else null
    if (outputPathForTests != null) {
      result.add(outputPathForTests)
    }

    val outputRoot = compilerOutputUrl
    if (outputRoot != null && outputRoot != outputPathForTests) {
      result.add(outputRoot)
    }

    return ArrayUtilRt.toStringArray(result)
  }
}