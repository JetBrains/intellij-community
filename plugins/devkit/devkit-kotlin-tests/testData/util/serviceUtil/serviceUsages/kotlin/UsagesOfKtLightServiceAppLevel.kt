@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.KtLightServiceAppLevel


fun foo3() {
  val service = <caret>KtLightServiceAppLevel.getInstance()
}