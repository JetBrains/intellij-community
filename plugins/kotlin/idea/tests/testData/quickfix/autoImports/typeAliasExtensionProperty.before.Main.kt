// "Import extension property 'A.ext'" "true"
// ERROR: Unresolved reference: ext

import dep.TA

fun use() {
    val ta = TA()
    ta.ext<caret>
}
/* IGNORE_FIR */