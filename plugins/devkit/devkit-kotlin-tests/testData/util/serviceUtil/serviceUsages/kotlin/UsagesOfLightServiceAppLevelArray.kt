@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import serviceDeclarations.LightServiceAppLevelArray


fun foo13() {
  val service = <caret>LightServiceAppLevelArray.getInstance()
}