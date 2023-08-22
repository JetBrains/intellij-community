// "Create class 'Foo'" "true"

fun <T> run(f: () -> T) = f()

fun test() {
    run { <caret>Foo() }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix