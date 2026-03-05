// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.engine

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjModule
import com.intellij.workspaceModel.codegen.engine.CodeGenerator
import com.intellij.workspaceModel.codegen.engine.GeneratedCode
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.GenerationResult
import com.intellij.workspaceModel.codegen.engine.GeneratorSettings
import com.intellij.workspaceModel.codegen.engine.ObjClassGeneratedCode
import com.intellij.workspaceModel.codegen.engine.ObjModuleFileGeneratedCode
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.checkExtensionFields
import com.intellij.workspaceModel.codegen.impl.writer.checkSuperTypes
import com.intellij.workspaceModel.codegen.impl.writer.checkSymbolicId
import com.intellij.workspaceModel.codegen.impl.writer.classes.implWsMetadataStorageBridgeCode
import com.intellij.workspaceModel.codegen.impl.writer.classes.implWsMetadataStorageCode
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage
import com.intellij.workspaceModel.codegen.impl.writer.fqn
import com.intellij.workspaceModel.codegen.impl.writer.generateCompatabilityBuilder
import com.intellij.workspaceModel.codegen.impl.writer.generateCompatibilityCompanion
import com.intellij.workspaceModel.codegen.impl.writer.generateTopLevelCode
import com.intellij.workspaceModel.codegen.impl.writer.implWsCode

class CodeGeneratorImpl : CodeGenerator {

  override fun generateEntitiesImplementation(module: CompiledObjModule, settings: GeneratorSettings): GenerationResult {
    setGeneratorSettings(settings)
    val reporter = ProblemReporterImpl()

    checkExtensionFields(module, reporter)

    if (reporter.hasErrors()) {
      return failedGenerationResult(reporter)
    }

    val generatedCode: MutableList<GeneratedCode> = arrayListOf()
    for (type in module.types) {
      try {
        checkSuperTypes(type, reporter)
        checkSymbolicId(type, reporter)
        if (reporter.hasErrors()) return failedGenerationResult(reporter)
        val topLevelCode = type.generateTopLevelCode(reporter)
        if (reporter.hasErrors()) return failedGenerationResult(reporter)
        val compatibilityBuilder = type.generateCompatabilityBuilder()
        val compatibilityCompanion = type.generateCompatibilityCompanion()
        val implementationClass = type.implWsCode(reporter)
        if (reporter.hasErrors()) return failedGenerationResult(reporter)
        generatedCode.add(
          ObjClassGeneratedCode(
            target = type,
            builderInterface = compatibilityBuilder,
            companionObject = compatibilityCompanion,
            topLevelCode = topLevelCode,
            implementationClass = implementationClass
          )
        )
      } catch (e: Exception) {
        // todo: pass reporter everywhere (search for 'throw UnsupportedOperationException' in this module)
        GenerationResult(emptyList(), listOf(GenerationProblem(e.message ?: "Failed to generate entity implementation for ${type.name}",
                                                               GenerationProblem.Level.ERROR,
                                                               ProblemLocation.Class(type))))
      }
    }

    return GenerationResult(generatedCode, reporter.problems)
  }

  override fun generateMetadataStoragesImplementation(modules: List<CompiledObjModule>, settings: GeneratorSettings): GenerationResult {
    setGeneratorSettings(settings)

    // Filter packages that contain any metadata and then sort them by name to guarantee the predictable order during regeneration
    val notEmptyModules = modules.filter { it.types.isNotEmpty() || it.abstractTypes.isNotEmpty() }.sortedBy { it.name }

    if (notEmptyModules.isEmpty()) {
      return GenerationResult(emptyList(), emptyList())
    }

    // One of the filtered packages will contain MetadataStorageImpl that stores metadata for the entire module
    // notEmptyModules are sorted by name, so we take the package with the minimum name
    val metadataStorageImplModule = notEmptyModules.first()
    // All other packages will contain MetadataStorageBridge
    val metadataStorageBridgeModules = notEmptyModules.drop(1)

    val generatedCode = arrayListOf<GeneratedCode>()

    addMetadataStorageCode(generatedCode,
                           metadataStorageImplModule,
                           implWsMetadataStorageCode(metadataStorageImplModule,
                                                     notEmptyModules.flatMap { it.types },
                                                     notEmptyModules.flatMap { it.abstractTypes }))

    val metadataStorageImplFqn = fqn(metadataStorageImplModule.implPackage, MetadataStorage.IMPL_NAME)
    metadataStorageBridgeModules.forEach {
      addMetadataStorageCode(generatedCode, it, it.implWsMetadataStorageBridgeCode(metadataStorageImplFqn))
    }

    return GenerationResult(generatedCode, emptyList())
  }

  private fun addMetadataStorageCode(generatedCode: MutableList<GeneratedCode>,
                                     objModule: ObjModule, metadataStorageGeneratedCode: String) {
    generatedCode.add(
      ObjModuleFileGeneratedCode(
        fileName = MetadataStorage.IMPL_NAME,
        objModuleName = objModule.name,
        generatedCode = metadataStorageGeneratedCode
      )
    )
  }

  private fun failedGenerationResult(reporter: ProblemReporter): GenerationResult =
    GenerationResult(emptyList(), reporter.problems)

  private fun setGeneratorSettings(settings: GeneratorSettings) {
    generatorSettings = settings
  }
}

internal lateinit var generatorSettings: GeneratorSettings

