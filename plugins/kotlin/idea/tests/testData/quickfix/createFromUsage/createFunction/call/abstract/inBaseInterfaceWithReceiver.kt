// "Create abstract function 'I.bar'" "true"

interface I

fun test(i: I) {
    i.<caret>bar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix