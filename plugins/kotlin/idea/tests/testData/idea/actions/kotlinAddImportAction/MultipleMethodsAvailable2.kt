// EXPECT_VARIANT_IN_ORDER "public fun ref(s: kotlin.String): kotlin.Unit defined in kotlinpackage.two.s2 in file MultipleMethodsAvailable2.dependency1.kt"
// EXPECT_VARIANT_IN_ORDER "public fun ref(s: kotlin.Int): kotlin.Unit defined in kotlinpackage.two in file MultipleMethodsAvailable2.dependency.kt"
package kotlinpackage.two.s1

fun foo() {
    myCall() {
        ref<caret>("a")
    }
}
