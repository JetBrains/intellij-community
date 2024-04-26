@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.module.Module

import serviceDeclarations.RegisteredModuleService


fun foo20(module: Module) {
  val service = <caret>RegisteredModuleService.getInstance(module)
}