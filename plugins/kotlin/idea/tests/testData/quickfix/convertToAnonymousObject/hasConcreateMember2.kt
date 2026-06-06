// "Convert to anonymous object" "true"
// K2_ERROR: Interface 'interface I : I0' does not have constructors.
interface I0 {
    fun x() {}
}

interface I : I0 {
    fun a()
    val b: Int
        get() = 1
}

fun foo(i: I) {}

fun test() {
    foo(<caret>I {})
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix