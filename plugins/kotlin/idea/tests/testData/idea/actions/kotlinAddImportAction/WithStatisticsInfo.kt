// EXPECT_VARIANT_IN_ORDER "public fun bar(n: kotlin.Int): kotlin.Unit defined in pack2 in file WithStatisticsInfo.dependency2.kt"
// EXPECT_VARIANT_IN_ORDER "public fun bar(n: kotlin.Int): kotlin.Unit defined in pack1 in file WithStatisticsInfo.dependency1.kt"
// INCREASE_USE_COUNT pack2###bar(kotlin.Int)
package root

fun test() {
    b<caret>ar(1)
}
