package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.generateCode
import com.intellij.workspaceModel.codegen.impl.dsl.optInWorkspaceEntityInternalApi
import com.intellij.workspaceModel.codegen.impl.dsl.packageDirective
import com.intellij.workspaceModel.codegen.impl.writer.MetadataStorage
import com.intellij.workspaceModel.codegen.impl.writer.QualifiedName
import com.intellij.workspaceModel.codegen.impl.writer.extensions.implPackage

internal fun GeneratorContext.metadataStorageBridgeCode(compiledObjModule: CompiledObjModule, metadataStorageImpl: QualifiedName): String = generateCode {
  packageDirective(compiledObjModule.implPackage)
  optInWorkspaceEntityInternalApi()
  +"internal object ${MetadataStorage.IMPL_NAME}: ${MetadataStorage.bridge}($metadataStorageImpl)"
}