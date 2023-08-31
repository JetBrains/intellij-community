// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun getValue(Any?, KProperty<*>): String"

import kotlin.reflect.KProperty

class Delegate() {
    operator fun <caret>getValue(thisRef: Any?, property: KProperty<*>): String = ":)"
}

val p: String by Delegate()


// IGNORE_K2_LOG