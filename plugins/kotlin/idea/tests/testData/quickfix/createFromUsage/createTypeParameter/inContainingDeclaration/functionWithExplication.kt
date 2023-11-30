// "Create type parameter 'X' in function 'foo'" "true"
class A<T>

fun foo(x: A<<caret>X>) {

}

fun test() {
    foo(A())
    foo(A<Int>())
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix