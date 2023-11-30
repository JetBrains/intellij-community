// "Create extension property 'Unit.foo'" "true"
// WITH_STDLIB

fun test() {
    val a: Int = Unit.<caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix