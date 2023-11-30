@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.KtLightServiceAppAndProjectLevelArray


fun foo1() {
  val service = <caret>KtLightServiceAppAndProjectLevelArray.getInstance()
}