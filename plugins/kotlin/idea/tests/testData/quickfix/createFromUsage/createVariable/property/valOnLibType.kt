// "Create extension property 'Int.foo'" "true"
// WITH_STDLIB

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2.<caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix