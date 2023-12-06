// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun setValue(Any?, KProperty<*>, String): Unit"

import kotlin.reflect.KProperty

class Delegate() {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String = ":)"

    operator fun <caret>setValue(thisRef: Any?, property: KProperty<*>, value: String) {

    }
}

var p: String by Delegate()


// IGNORE_K2_LOG