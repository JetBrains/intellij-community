// "Create extension property 'Int.foo'" "true"
// WITH_STDLIB

class A<T>(val n: T)

fun test() {
    2.<caret>foo = A("2")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix