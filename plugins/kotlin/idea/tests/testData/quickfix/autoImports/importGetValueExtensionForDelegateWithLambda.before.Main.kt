// "Import operator 'MyDelegate.getValue'" "true"
// WITH_STDLIB
// ERROR: Type 'MyDelegate<TypeVariable(T)>' has no method 'getValue(Nothing?, KProperty<*>)' and thus it cannot serve as a delegate

package import

import base.MyDelegate

val myVal by <caret>MyDelegate { false }
/* IGNORE_FIR */