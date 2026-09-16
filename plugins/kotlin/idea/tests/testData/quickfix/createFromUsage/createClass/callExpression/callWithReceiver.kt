// "Create class 'Foo'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T>(val n: T) {

}

fun test() {
    val a = A(1).<caret>Foo(2)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction