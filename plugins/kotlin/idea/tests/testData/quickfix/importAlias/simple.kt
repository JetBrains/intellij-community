// "Introduce import alias" "true"
// WITH_STDLIB
// ACTION: Add explicit type arguments
// ACTION: Convert to 'forEachIndexed'
// ACTION: Introduce import alias
// ACTION: Replace with a 'for' loop
fun foo() {
    listOf("a", "b", "c").<caret>forEach { }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.IntroduceImportAliasIntention