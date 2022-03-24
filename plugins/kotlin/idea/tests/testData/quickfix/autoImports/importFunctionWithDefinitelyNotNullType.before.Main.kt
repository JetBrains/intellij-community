// "Import" "true"
// ERROR: Unresolved reference: defNotNull
// COMPILER_ARGUMENTS: -XXLanguage:+DefinitelyNonNullableTypes
package pckg.useSite

fun test() {
    "x".<caret>defNotNull("x")
}
/* IGNORE_FIR */