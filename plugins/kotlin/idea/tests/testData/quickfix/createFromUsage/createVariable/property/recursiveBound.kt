// "Create property 'foo'" "true"
// ERROR: Property must be initialized
class Cyclic<E : Cyclic<E>>

fun test() {
    val c : Cyclic<*> = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix