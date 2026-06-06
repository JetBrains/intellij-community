// "Convert to anonymous object" "true"
// K2_ERROR: Interface 'interface I : Any' does not have constructors.
interface I {
    fun foo(): String
}

fun test() {
    <caret>I {
        return@I ""
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix