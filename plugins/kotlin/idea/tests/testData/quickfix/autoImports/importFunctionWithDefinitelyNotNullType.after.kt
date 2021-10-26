// "Import" "true"
// ERROR: Unresolved reference: defNotNull
// COMPILER_ARGUMENTS: -XXLanguage:+DefinitelyNonNullableTypes
package pckg.useSite

import pckg.dep.defNotNull

fun test() {
    "x".<caret>defNotNull("x")
}
/* IGNORE_FIR */