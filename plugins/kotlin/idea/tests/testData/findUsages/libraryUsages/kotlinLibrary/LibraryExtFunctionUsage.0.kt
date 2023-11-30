// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun ReceiverClass.extFun(): Unit"
// FIND_BY_REF

package usages

import library.*

class K {
    fun foo(rc: ReceiverClass) {
        rc.ext<caret>Fun()
    }
}