@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.NonService


fun foo18() {
  val service = <caret>NonService.getInstance()
}