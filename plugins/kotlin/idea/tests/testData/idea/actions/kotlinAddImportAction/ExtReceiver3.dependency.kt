// EXPECT_VARIANT_IN_ORDER "public fun kotlin.Int.seconds(s: kotlin.String): kotlin.Int defined in p1 in file ExtReceiver.dependency.kt"
// EXPECT_VARIANT_IN_ORDER "public fun kotlin.Int.seconds(i: kotlin.Int): kotlin.Int defined in p2 in file ExtReceiver.dependency1.kt"
package p1

fun sec(s: String) {}