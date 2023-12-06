// "Create class 'Foo'" "true"
interface I

fun <T : I> foo() {}

fun x() {
    foo<<caret>Foo>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix