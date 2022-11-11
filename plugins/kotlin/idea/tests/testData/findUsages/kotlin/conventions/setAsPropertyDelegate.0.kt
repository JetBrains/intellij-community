// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

import kotlin.reflect.KProperty

class Delegate() {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String = ":)"

    operator fun <caret>setValue(thisRef: Any?, property: KProperty<*>, value: String) {

    }
}

var p: String by Delegate()

// FIR_COMPARISON
// IGNORE_FIR_LOG