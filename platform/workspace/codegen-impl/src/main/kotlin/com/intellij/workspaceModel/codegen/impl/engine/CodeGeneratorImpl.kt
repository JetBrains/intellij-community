// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.engine

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjModule
import com.intellij.workspaceModel.codegen.engine.*
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.classes.implWsMetadataStorageClassCode

class CodeGeneratorImpl : CodeGenerator {
  override fun generate(module: CompiledObjModule): GenerationResult {
    val reporter = ProblemReporterImpl()

    checkExtensionFields(module, reporter)

    if (reporter.hasErrors()) {
      return failedGenerationResult(reporter)
    }

    val objClassToBuilderInterface = module.types.associateWith {
      val builderInterface = it.generateBuilderCode(reporter)
      builderInterface
    }

    if (reporter.hasErrors()) {
      return failedGenerationResult(reporter)
    }

    val metadataStorageImplGeneratedCode = module.implWsMetadataStorageClassCode

    val generatedCode = arrayListOf<GeneratedCode>()
    addObjClassesCode(generatedCode, objClassToBuilderInterface)
    addObjModuleCode(generatedCode, module, metadataStorageImplGeneratedCode)

    return GenerationResult(generatedCode, reporter.problems)
  }

  private fun addObjClassesCode(generatedCode: MutableList<GeneratedCode>,
                                objClassToBuilderInterface: Map<ObjClass<*>, String>) {
    generatedCode.addAll(objClassToBuilderInterface.map { (objClass, builderInterface) ->
      ObjClassGeneratedCode(
        target = objClass,
        builderInterface = builderInterface,
        companionObject = objClass.generateCompanionObject(),
        topLevelCode = objClass.generateExtensionCode(),
        implementationClass = objClass.implWsCode()
      )
    })
  }

  private fun addObjModuleCode(generatedCode: MutableList<GeneratedCode>,
                               objModule: ObjModule, metadataStorageImplGeneratedCode: String?) {
    if (metadataStorageImplGeneratedCode != null) {
      generatedCode.add(ObjModuleFileGeneratedCode(
        fileName = MetadataStorage.IMPL_NAME,
        objModuleName = objModule.name,
        generatedCode = metadataStorageImplGeneratedCode
      ))
    }
  }

  private fun failedGenerationResult(reporter: ProblemReporter): GenerationResult =
    GenerationResult(emptyList(), reporter.problems)
}

