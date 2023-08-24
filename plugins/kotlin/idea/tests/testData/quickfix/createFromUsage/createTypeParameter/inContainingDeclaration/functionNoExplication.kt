// "Create type parameter 'X' in function 'foo'" "true"
fun foo(x: <caret>X) {

}

fun test() {
    foo(1)
    foo("2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix