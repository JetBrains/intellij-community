@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.KtRegisteredApplicationService


fun foo7() {
  val service = <caret>KtRegisteredApplicationService.getInstance()
}