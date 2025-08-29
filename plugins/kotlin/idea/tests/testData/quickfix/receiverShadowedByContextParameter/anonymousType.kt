// "Use 'contextOf<Foo>()' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// API_VERSION: 2.2

interface Foo {
    fun bar() {
    }
}

fun Foo.test() {
    val o = object : Foo {}
    context(o) {
        bar<caret>()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterReceiverFix
