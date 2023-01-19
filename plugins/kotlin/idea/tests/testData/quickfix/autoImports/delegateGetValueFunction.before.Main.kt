// "Import extension function 'A.getValue'" "true"
// ERROR: Property delegate must have a 'getValue(Some, KProperty<*>)' method. None of the following functions is suitable: <br>public final fun getValue(): Unit defined in kotlinpackage.one.A
// WITH_STDLIB
package kotlinpackage.two

import kotlinpackage.one.A
import kotlinpackage.one.B

class Some(a: A, b: B) {
    val x1 by a<caret>
}

/* IGNORE_FIR */