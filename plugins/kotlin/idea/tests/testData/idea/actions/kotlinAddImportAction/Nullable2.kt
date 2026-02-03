// EXPECT_VARIANT_IN_ORDER "public fun root.A.to3(): kotlin.Unit defined in p1 in file Nullable2.dependency.kt"
// EXPECT_VARIANT_IN_ORDER "public fun root.A?.to3(): kotlin.Unit defined in p2 in file Nullable2.dependency1.kt"

package root


class A

fun foo(a: A) {
    a.<caret>to3()
}
