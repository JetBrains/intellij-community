// "Create type parameter 'X' in function 'foo'" "true"
class A<T : List<Int>>

fun foo(x: A<<caret>X>) {

}

fun test() {
    foo(A())
    foo(A<List<Int>>())
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix