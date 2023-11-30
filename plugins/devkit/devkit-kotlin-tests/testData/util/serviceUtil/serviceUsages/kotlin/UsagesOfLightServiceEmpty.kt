@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.LightServiceEmpty


fun foo14() {
  val service = <caret>LightServiceEmpty.getInstance()
}