// "Create class 'Foo'" "true"

object A {

}

fun test() {
    val a = A.<caret>Foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix