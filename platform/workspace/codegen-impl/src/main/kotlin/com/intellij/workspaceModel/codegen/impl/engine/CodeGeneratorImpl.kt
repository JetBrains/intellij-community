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
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.generatorContext
import com.intellij.workspaceModel.codegen.impl.metadata.metadataImpl
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.checkExtensionFields
import com.intellij.workspaceModel.codegen.impl.writer.checkReferences
import com.intellij.workspaceModel.codegen.impl.writer.checkSuperTypes
import com.intellij.workspaceModel.codegen.impl.writer.checkSymbolicId
import com.intellij.workspaceModel.codegen.impl.metadata.metadataStorageBridgeCode
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage
import com.intellij.workspaceModel.codegen.impl.writer.fqn
import com.intellij.workspaceModel.codegen.impl.writer.entityImplementation.generateImplementationFile
import com.intellij.workspaceModel.codegen.impl.writer.entityApi.generateTopLevelCode

class CodeGeneratorImpl : CodeGenerator {
  override fun generateEntitiesImplementation(module: CompiledObjModule, settings: GeneratorSettings): GenerationResult =
    generatorContext(settings) {
      checkExtensionFields(module)

      val generatedCode: MutableList<GeneratedCode> = arrayListOf()
      for (type in module.types) {
        try {
          checkSuperTypes(type)
          checkSymbolicId(type)
          checkReferences(type)
          val topLevelCode = generateTopLevelCode(type)
          val implementationClass = generateImplementationFile(type)
          generatedCode.add(
            ObjClassGeneratedCode(
              target = type,
              topLevelCode = topLevelCode,
              implementationClass = implementationClass,
              builderInterface = "",
              companionObject = "",
            )
          )
        }
        catch (e: Exception) {
          return@generatorContext GenerationResult(emptyList(),
                                                   listOf(GenerationProblem(e.message
                                                                            ?: "Failed to generate entity implementation for ${type.name}",
                                                                            GenerationProblem.Level.ERROR,
                                                                            ProblemLocation.Class(type))))
        }
      }

      if (hasErrors()) return@generatorContext failedGenerationResult()
      return@generatorContext GenerationResult(generatedCode, problems)
    }

  override fun generateMetadataStoragesImplementation(modules: List<CompiledObjModule>, settings: GeneratorSettings): GenerationResult =
    generatorContext(settings) {
      // Filter packages that contain any metadata and then sort them by name to guarantee the predictable order during regeneration
      val notEmptyModules = modules.filter { it.types.isNotEmpty() || it.abstractTypes.isNotEmpty() }.sortedBy { it.name }

      if (notEmptyModules.isEmpty()) {
        return@generatorContext GenerationResult(emptyList(), emptyList())
      }

      // One of the filtered packages will contain MetadataStorageImpl that stores metadata for the entire module
      // notEmptyModules are sorted by name, so we take the package with the minimum name
      val metadataStorageImplModule = notEmptyModules.first()
      // All other packages will contain MetadataStorageBridge
      val metadataStorageBridgeModules = notEmptyModules.drop(1)

      val generatedCode = arrayListOf<GeneratedCode>()

      val metadataStorageImplCode = metadataImpl(metadataStorageImplModule, notEmptyModules)
      addMetadataStorageCode(generatedCode, metadataStorageImplModule, metadataStorageImplCode)

      val metadataStorageImplFqn = fqn(metadataStorageImplModule.implPackage, MetadataStorage.IMPL_NAME)
      for (module in metadataStorageBridgeModules) {
        val metadataBridgeCode = metadataStorageBridgeCode(module, metadataStorageImplFqn)
        addMetadataStorageCode(generatedCode, module, metadataBridgeCode)
      }

      return@generatorContext GenerationResult(generatedCode, problems)
    }

  private fun addMetadataStorageCode(
    generatedCode: MutableList<GeneratedCode>,
    objModule: ObjModule, 
    metadataStorageGeneratedCode: String,
  ) {
    generatedCode.add(
      ObjModuleFileGeneratedCode(
        fileName = MetadataStorage.IMPL_NAME,
        objModuleName = objModule.name,
        generatedCode = metadataStorageGeneratedCode
      )
    )
  }

  private fun GeneratorContext.failedGenerationResult(): GenerationResult =
    GenerationResult(emptyList(), problems)
}

class GenerationException(message: String) :
  RuntimeException("An exception was thrown in the generator instead of a problem being reported: $message")
