package sample

import sample.A.Companion.companionExtensionFunction

fun testBottom() {
    A().memberFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    A().memberFunction(42)
    A().memberFunction(42, "ok")

    topLevelFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    topLevelFunction(42)
    topLevelFunction(42, "ok")

    "".topLevelExtensionFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    "".topLevelExtensionFunction(42)
    "".topLevelExtensionFunction(42, "ok")
}

fun A.testBottom() {
    "".companionExtensionFunction(<!NO_VALUE_FOR_PARAMETER!>)<!>
    "".companionExtensionFunction(42)
    "".companionExtensionFunction(42, "ok")
}
