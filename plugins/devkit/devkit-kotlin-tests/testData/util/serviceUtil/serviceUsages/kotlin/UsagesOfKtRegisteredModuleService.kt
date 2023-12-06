@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.module.Module

import serviceDeclarations.KtRegisteredModuleService


fun foo8(module: Module) {
  val service = <caret>KtRegisteredModuleService.getInstance(module)
}