// "Create secondary constructor" "true"
// ERROR: None of the following functions can be called with the arguments supplied: <br>public constructor Foo(n: Int) defined in Foo<br>public constructor Foo(n: String) defined in Foo
class Foo(val n: Int)

fun test() {
    val foo = Foo("a<caret>bc${1}")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix