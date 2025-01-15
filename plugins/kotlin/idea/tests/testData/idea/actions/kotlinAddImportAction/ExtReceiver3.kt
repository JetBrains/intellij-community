// WITH_STDLIB
// EXPECT_VARIANT_IN_ORDER "public fun sec(s: kotlin.String): kotlin.Unit defined in p1 in file ExtReceiver3.dependency.kt"
// EXPECT_VARIANT_IN_ORDER "public fun root.A.sec(s: kotlin.String): kotlin.Unit defined in p2 in file ExtReceiver3.dependency1.kt"
package root

class A {
    fun a() {
        listOf("a").forEach {
            sec<caret>(it)
        }
    }
}