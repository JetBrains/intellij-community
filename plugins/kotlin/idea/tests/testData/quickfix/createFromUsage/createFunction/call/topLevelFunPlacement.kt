// "Create function 'foo'" "true"

val baz = 1

fun test() {
    val a: Int = <caret>foo()
}

fun bar() {

}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix