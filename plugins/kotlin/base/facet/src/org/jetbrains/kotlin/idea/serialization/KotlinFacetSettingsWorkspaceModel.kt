// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.serialization

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.copyOf
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.workspaceModel.*
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinFacetSettingsWorkspaceModel(val entity: KotlinSettingsEntity.Builder) : IKotlinFacetSettings {
    private var myUseProjectSettings = entity.useProjectSettings
    override var useProjectSettings: Boolean
        get() = myUseProjectSettings
        set(value) {
            entity.useProjectSettings = value
            myUseProjectSettings = value
        }

    override var version: Int
        get() = entity.version
        set(value) {
            entity.version = value
        }

    override fun updateMergedArguments() {
        // Do nothing
    }

    private fun computeMergedArguments(): CommonCompilerArguments? {
        val compilerArguments = compilerArguments
        val compilerSettings = compilerSettings

        return compilerArguments?.copyOf()?.apply {
            if (compilerSettings != null) {
                parseCommandLineArguments(compilerSettings.additionalArgumentsAsList, this)
            }
            if (this is K2JVMCompilerArguments) this.classpath = ""
        }
    }

    private var _additionalVisibleModuleNames: Set<String> = entity.additionalVisibleModuleNames
    override var additionalVisibleModuleNames: Set<String>
        get() = _additionalVisibleModuleNames
        set(value) {
            entity.additionalVisibleModuleNames = value.toMutableSet()
            _additionalVisibleModuleNames = value
        }

    private var _compilerArguments: CommonCompilerArguments? = null
    override var compilerArguments: CommonCompilerArguments?
        get() {
            if (_compilerArguments != null) {
                return _compilerArguments
            }

            val serializedArguments = entity.compilerArguments
            _compilerArguments = if (serializedArguments.isNotEmpty()) {
                CompilerArgumentsSerializer.deserializeFromString(serializedArguments)
            } else {
                null
            }

            return _compilerArguments
        }
        set(value) {
            entity.compilerArguments = CompilerArgumentsSerializer.serializeToString(value)
            updateMergedArguments()
            _compilerArguments = value
        }

    override val mergedCompilerArguments: CommonCompilerArguments?
        get() {
            return computeMergedArguments()
        }

    override var apiLevel: LanguageVersion?
        get() = compilerArguments?.apiVersion?.let { LanguageVersion.fromFullVersionString(it) }
        set(value) {
            updateCompilerArguments {
                apiVersion = value?.versionString
            }
        }

    private var _compilerSettings: CompilerSettings? = null
    override var compilerSettings: CompilerSettings?
        get() {
            if (_compilerSettings != null) {
                return _compilerSettings
            }

            val compilerSettingsData = entity.compilerSettings
            if (!compilerSettingsData.isInitialized) return null

            _compilerSettings = compilerSettingsData.toCompilerSettings { newSettings ->
                entity.compilerSettings = newSettings.toCompilerSettingsData()
                updateMergedArguments()
            }

            return _compilerSettings
        }
        set(value) {
            entity.compilerSettings = value.toCompilerSettingsData()
            updateMergedArguments()
            _compilerSettings = value
        }


    private var _dependsOnModuleNames: List<String> = entity.dependsOnModuleNames
    override var dependsOnModuleNames: List<String>
        get() = _dependsOnModuleNames
        set(value) {
            entity.dependsOnModuleNames = value.toMutableList()
            _dependsOnModuleNames = value
        }

    private var _externalProjectId = entity.externalProjectId
    override var externalProjectId: String
        get() = _externalProjectId
        set(value) {
            entity.externalProjectId = value
            _externalProjectId = value
        }

    override var externalSystemRunTasks: List<ExternalSystemRunTask>
        get() = entity.externalSystemRunTasks.map { deserializeExternalSystemTestRunTask(it) }
        set(value) {
            entity.externalSystemRunTasks = value.map { it.serializeExternalSystemTestRunTask() }.toMutableList()
        }

    private var _implementedModuleNames: List<String> = entity.implementedModuleNames
    override var implementedModuleNames: List<String>
        get() = _implementedModuleNames
        set(value) {
            entity.implementedModuleNames = value.toMutableList()
            _implementedModuleNames = value
        }

    private var _isHmppEnabled = entity.isHmppEnabled
    override var isHmppEnabled: Boolean
        get() = _isHmppEnabled
        set(value) {
            entity.isHmppEnabled = value
            _isHmppEnabled = value
        }

    private var _isTestModule = entity.isTestModule
    override var isTestModule: Boolean
        get() = _isTestModule
        set(value) {
            entity.isTestModule = value
            _isTestModule = value
        }

    private var _kind = entity.kind
    override var kind: KotlinModuleKind
        get() = _kind
        set(value) {
            entity.kind = value
            _kind = value
        }

    override var languageLevel: LanguageVersion?
        get() = compilerArguments?.languageVersion?.let { LanguageVersion.fromFullVersionString(it) }
        set(value) {
            updateCompilerArguments {
                languageVersion = value?.versionString
            }
        }


    override val mppVersion: KotlinMultiplatformVersion?
        @Suppress("DEPRECATION")
        get() = when {
            isHmppEnabled -> KotlinMultiplatformVersion.M3
            kind.isNewMPP -> KotlinMultiplatformVersion.M2
            targetPlatform.isCommon() || implementedModuleNames.isNotEmpty() -> KotlinMultiplatformVersion.M1
            else -> null
        }

    private var _productionOutputPath: String? = entity.productionOutputPath
    override var productionOutputPath: String?
        get() = _productionOutputPath
        set(value) {
            _productionOutputPath = value
            entity.productionOutputPath = value ?: ""
        }

    private var _pureKotlinSourceFolders: List<String> = entity.pureKotlinSourceFolders
    override var pureKotlinSourceFolders: List<String>
        get() = _pureKotlinSourceFolders
        set(value) {
            entity.pureKotlinSourceFolders = value.toMutableList()
            _pureKotlinSourceFolders = value
        }

    private var _sourceSetNames: List<String> = entity.sourceSetNames
    override var sourceSetNames: List<String>
        get() = _sourceSetNames
        set(value) {
            _sourceSetNames = value
            entity.sourceSetNames = value.toMutableList()
        }

    private var _targetPlatform: TargetPlatform? = null
    override var targetPlatform: TargetPlatform?
        get() {
            if (_targetPlatform != null) {
                return _targetPlatform
            }

            val args = compilerArguments
            val deserializedTargetPlatform =
                entity.targetPlatform.takeIf { it.isNotEmpty() }.deserializeTargetPlatformByComponentPlatforms()
            val singleSimplePlatform = deserializedTargetPlatform?.componentPlatforms?.singleOrNull()
            if (singleSimplePlatform == JvmPlatforms.defaultJvmPlatform.singleOrNull() && args != null) {
                _targetPlatform = IdePlatformKind.platformByCompilerArguments(args)
                return _targetPlatform
            }
            return deserializedTargetPlatform
        }
        set(value) {
            entity.targetPlatform = value?.serializeComponentPlatforms() ?: ""
            _targetPlatform = value
        }

    private var _testOutputPath: String? = entity.testOutputPath
    override var testOutputPath: String?
        get() = _testOutputPath
        set(value) {
            entity.testOutputPath = value ?: ""
            _testOutputPath = value
        }
}

fun IKotlinFacetSettings.updateCompilerArguments(block: CommonCompilerArguments.() -> Unit) {
    val compilerArguments = this.compilerArguments ?: return
    block(compilerArguments)
    if (this is KotlinFacetSettingsWorkspaceModel) {
        this.compilerArguments = compilerArguments
    }
}