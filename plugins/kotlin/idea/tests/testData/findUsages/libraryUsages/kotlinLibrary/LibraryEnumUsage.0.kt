// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun fooEnum(Int): Int"
// FIND_BY_REF

package usages

import library.*

fun test(e: EnumWithEnumEntries) {
    e.fo<caret>oEnum(4)
    EnumWithEnumEntries.AnEntry.fooEnum(4)
}

