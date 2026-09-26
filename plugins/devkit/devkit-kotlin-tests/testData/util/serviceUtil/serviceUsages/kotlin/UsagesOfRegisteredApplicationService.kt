@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.RegisteredApplicationService


fun foo19() {
  val service = <caret>RegisteredApplicationService.getInstance()
}