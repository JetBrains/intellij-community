// "Use 'this' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters

class Foo {
    val bar get() = ""
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    withFoo {
        ba<caret>r
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitThisFix
