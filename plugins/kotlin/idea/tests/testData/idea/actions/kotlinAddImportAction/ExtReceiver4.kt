// WITH_STDLIB
// EXPECT_VARIANT_IN_ORDER "public fun kotlin.Int.sec(s: kotlin.String): kotlin.Unit defined in p2 in file ExtReceiver4.dependency1.kt"
// EXPECT_VARIANT_IN_ORDER "public fun sec(s: kotlin.String): kotlin.Unit defined in p1 in file ExtReceiver4.dependency.kt"
package root

fun foo() {
    val x = with(1) {
        sec<caret>("a")
    }
}