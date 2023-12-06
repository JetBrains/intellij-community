@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.KtNonService


fun foo6() {
  val service = <caret>KtNonService.getInstance()
}