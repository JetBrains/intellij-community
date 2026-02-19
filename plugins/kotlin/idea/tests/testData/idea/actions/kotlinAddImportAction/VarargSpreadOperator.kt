// EXPECT_VARIANT_IN_ORDER "public fun foo(vararg n: kotlin.Int): kotlin.Unit defined in pack2 in file VarargSpreadOperator.dependency2.kt"
// EXPECT_VARIANT_IN_ORDER "public fun foo(n: kotlin.Int): kotlin.Unit defined in pack1 in file VarargSpreadOperator.dependency1.kt"
package root

fun test() {
    val a = intArrayOf(1, 1)
    f<caret>oo(*a)
}
