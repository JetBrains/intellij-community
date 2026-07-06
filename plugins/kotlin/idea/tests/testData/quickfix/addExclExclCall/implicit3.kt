// "Add non-null asserted (project!!) call" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
class Foo {
    val project: Project? = null

    fun quux() {
        baz(<caret>project)
    }

    fun baz(project: Project) {}

    class Project
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix