// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// PSI_ELEMENT_AS_TITLE: single
// OPTIONS: usages
// FIND_BY_REF
// WITH_FILE_NAME

package usages

fun test() {
    val f = mapOf(Pair("1", "2")).keys.<caret>single({ true })
}
