// "Import class 'XXX'" "true"
// ERROR: Unresolved reference: XXX

import dependency2.YYY

fun foo(x: XXX<caret>) {
}

/* IGNORE_FIR */