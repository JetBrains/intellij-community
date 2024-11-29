// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
package test;

enum class A {
    A1, A2;

    companion object {
        val entries = ""
    }
}

fun foo() {
    A.A1
    A.A2
    <caret>A.Companion.entries
}
