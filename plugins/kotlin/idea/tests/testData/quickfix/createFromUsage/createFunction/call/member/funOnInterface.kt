// "Create member function 'T.foo'" "true"

interface T

fun test(t: T) {
    val b: Boolean = t.<caret>foo("1", 2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix