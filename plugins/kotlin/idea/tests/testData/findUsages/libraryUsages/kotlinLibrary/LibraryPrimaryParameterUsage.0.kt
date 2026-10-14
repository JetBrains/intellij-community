// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val constrProp: String"
// FIND_BY_REF

package usages

import library.*

fun test(v: WithConstructorProperty) {
    val p = v.constr<caret>Prop
}

