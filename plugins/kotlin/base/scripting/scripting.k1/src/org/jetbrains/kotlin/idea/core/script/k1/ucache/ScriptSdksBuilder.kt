// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k1.configuration.utils.ScriptClassRootsStorage
import org.jetbrains.kotlin.idea.core.script.k1.ucache.SdkId
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import java.nio.file.Path

class ScriptSdksBuilder(
  val project: Project,
  val sdks: MutableMap<SdkId, Sdk?> = mutableMapOf(),
  private val remove: Sdk? = null
) {
    private val defaultSdk by lazy { getScriptDefaultSdk() }

    fun build(): ScriptSdks {
        val nonIndexedSdks = sdks.values.filterNotNullTo(mutableSetOf())
        val nonIndexedClassRoots = mutableSetOf<VirtualFile>()
        val nonIndexedSourceRoots = mutableSetOf<VirtualFile>()

      runReadAction {
        for (module in ModuleManager.getInstance(project).modules.filter { !it.isDisposed }) {
          ProgressManager.checkCanceled()
          if (nonIndexedSdks.isEmpty()) break
          nonIndexedSdks.remove(ModuleRootManager.getInstance(module).sdk)
        }

        nonIndexedSdks.forEach { sdk ->
          nonIndexedClassRoots += sdk.rootProvider.getFiles(OrderRootType.CLASSES)
          nonIndexedSourceRoots += sdk.rootProvider.getFiles(OrderRootType.SOURCES)
        }
      }

        return ScriptSdks(sdks, nonIndexedClassRoots, nonIndexedSourceRoots)
    }

    @Deprecated("Don't use, used only from DefaultScriptingSupport for saving to storage")
    fun addAll(other: ScriptSdksBuilder) {
        sdks.putAll(other.sdks)
    }

    fun addAll(other: ScriptSdks) {
        sdks.putAll(other.sdks)
    }

    // add sdk by home path with checking for removed sdk
    fun addSdk(sdkId: SdkId): Sdk? {
        val canonicalPath = sdkId.homeDirectory ?: return addDefaultSdk()
        return addSdk(Path.of(canonicalPath))
    }

    fun addSdk(javaHome: Path?): Sdk? {
        if (javaHome == null) return addDefaultSdk()

        return sdks.getOrPut(SdkId.Companion(javaHome)) {
            getScriptSdkByJavaHome(javaHome) ?: defaultSdk
        }
    }

    private fun getScriptSdkByJavaHome(javaHome: Path): Sdk? {
        // workaround for mismatched gradle wrapper and plugin version
        val javaHomeVF = try {
            VfsUtil.findFile(javaHome, true)
        } catch (e: Throwable) {
            null
        } ?: return null

        return ProjectJdkTable.getInstance().allJdks
            .firstOrNull { it.homeDirectory == javaHomeVF && it.canBeUsedForScript()}
    }

    private fun addDefaultSdk(): Sdk? =
        sdks.getOrPut(SdkId.default) { defaultSdk }

    private fun addSdkByName(sdkName: String) {
        val sdk = ProjectJdkTable.getInstance().allJdks
            .find { it.name == sdkName }
            ?.takeIf { it.canBeUsedForScript() }
                  ?: defaultSdk
                  ?: return

        val homePath = sdk.homePath ?: return
        sdks[SdkId.Companion(homePath)] = sdk
    }

    private fun getScriptDefaultSdk(): Sdk? {
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.canBeUsedForScript() }
        if (projectSdk != null) return projectSdk

        val allJdks = ProjectJdkTable.getInstance().allJdks

        val anyJavaSdk = allJdks.find { it.canBeUsedForScript() }
        if (anyJavaSdk != null) {
            return anyJavaSdk
        }

      scriptingWarnLog(
        "Default Script SDK is null: " +
        "projectSdk = ${ProjectRootManager.getInstance(project).projectSdk}, " +
        "all sdks = ${allJdks.joinToString("\n")}"
      )

        return null
    }

    private fun Sdk.canBeUsedForScript() = this != remove && sdkType is JavaSdkType && hasValidClassPathRoots()

    private fun Sdk.hasValidClassPathRoots(): Boolean {
        val rootClasses = rootProvider.getFiles(OrderRootType.CLASSES)
        return rootClasses.isNotEmpty() && rootClasses.all { it.isValid }
    }

    fun toStorage(storage: ScriptClassRootsStorage) {
        storage.sdks = sdks.values.mapNotNullTo(mutableSetOf()) { it?.name }
        storage.defaultSdkUsed = sdks.containsKey(SdkId.default)
    }

    fun fromStorage(storage: ScriptClassRootsStorage) {
        storage.sdks.forEach {
            addSdkByName(it)
        }
        if (storage.defaultSdkUsed) {
            addDefaultSdk()
        }
    }
}