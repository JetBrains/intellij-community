// "Import operator 'SomeType.invoke'" "true"
// WITH_STDLIB
// ERROR: Expression 'topVal' of type 'SomeType' cannot be invoked as a function. The function 'invoke()' is not found

package mig

import another.invoke
import another.topVal

fun use() {
    topVal<selection></selection>()
}
/* IGNORE_FIR */