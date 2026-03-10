// "Use 'this' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Call to 'val bar: String' defined in 'Foo' uses an implicit receiver shadowed by a context parameter. Make the receiver explicit using 'this' or 'contextOf<Foo>()'.

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
