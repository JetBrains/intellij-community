// "Import extension property 'A.ext'" "true"
// ERROR: Unresolved reference: ext

import dep.TA
import dep.ext

fun use() {
    val ta = TA()
    ta.ext<caret>
}
/* IGNORE_FIR */