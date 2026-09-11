// "Create class 'Foo'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T>(val n: T) {

}

fun <U> test(u: U) {
    val a = A(u).<caret>Foo(u)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction