// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.serialization

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.copyOf
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.workspaceModel.KotlinModuleSettingsSerializer
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.idea.workspaceModel.toCompilerSettingsData
import org.jetbrains.kotlin.idea.workspaceModel.toCompilerSettings
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.toTargetPlatform

class KotlinFacetSettingsWorkspaceModel(val entity: KotlinSettingsEntity.Builder) : IKotlinFacetSettings {
    private var myUseProjectSettings = entity.useProjectSettings
    override var useProjectSettings: Boolean
        get() = myUseProjectSettings
        set(value) {
            entity.useProjectSettings = value
            myUseProjectSettings = value
        }

    override var version: Int = KotlinFacetSettings.CURRENT_VERSION

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

    override var compilerArguments: CommonCompilerArguments? =
        if (entity.compilerArguments == "") null else KotlinModuleSettingsSerializer.serializeFromString(entity.compilerArguments) as? CommonCompilerArguments
        set(value) {
            field = value
            updateMergedArguments()
        }

    override val mergedCompilerArguments: CommonCompilerArguments?
        get() {
            return computeMergedArguments()
        }

    override var apiLevel: LanguageVersion?
        get() = compilerArguments?.apiVersion?.let { LanguageVersion.fromFullVersionString(it) }
        set(value) {
            compilerArguments?.updateCompilerArguments {
                apiVersion = value?.versionString
            }
        }

    private var _compilerSettings: CompilerSettings? = entity.compilerSettings.toCompilerSettings()
    override var compilerSettings: CompilerSettings?
        get() = _compilerSettings
        set(value) {
            entity.compilerSettings = value.toCompilerSettingsData()
            _compilerSettings = value
            updateMergedArguments()
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

    private var _externalSystemRunTasks: List<ExternalSystemRunTask> = emptyList()
    override var externalSystemRunTasks: List<ExternalSystemRunTask>
        get() = _externalSystemRunTasks
        set(value) {
            // This class is not stored in workspace model entity because it is needed only for MPP
            // As far as there is no plans to migrate Gradle import on new workspace model in the nearest future, it will be absent in entity
            // But serialization definitely needs to be implemented in process of migrating Gradle import
            _externalSystemRunTasks = value
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
            compilerArguments?.updateCompilerArguments {
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

    private var _targetPlatform : TargetPlatform? = JvmPlatforms.defaultJvmPlatform.singleOrNull()?.toTargetPlatform()
    override var targetPlatform: TargetPlatform?
        get() {
            val args = compilerArguments
            val singleSimplePlatform = _targetPlatform?.componentPlatforms?.singleOrNull()
            if (singleSimplePlatform == JvmPlatforms.defaultJvmPlatform.singleOrNull() && args != null) {
                return IdePlatformKind.platformByCompilerArguments(args)
            }
            return _targetPlatform
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

    private fun CommonCompilerArguments.updateCompilerArguments(block: CommonCompilerArguments.() -> Unit) {
        this.block()
        entity.compilerArguments = KotlinModuleSettingsSerializer.serializeToString(this)
    }
}