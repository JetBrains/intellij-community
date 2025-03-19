// "Create extension function 'Int.foo'" "true"
// WITH_STDLIB
fun <T, U> T.map(f: T.() -> U) = f()

fun consume(s: String) {}

fun test() {
    consume(1.map(Int::<caret>foo))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix