@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.LightServiceAppLevel


fun foo12() {
  val service = <caret>LightServiceAppLevel.getInstance()
}