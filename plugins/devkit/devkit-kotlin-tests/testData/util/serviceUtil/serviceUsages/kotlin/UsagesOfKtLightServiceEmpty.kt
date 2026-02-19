@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.KtLightServiceEmpty


fun foo4() {
  val service = <caret>KtLightServiceEmpty.getInstance()
}