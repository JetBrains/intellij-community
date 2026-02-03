// IGNORE_K2
// WITH_STDLIB
// EXPECT_VARIANT_IN_ORDER "public inline fun min(a: kotlin.Int, b: kotlin.Int): kotlin.Int defined in kotlin.math"
// EXPECT_VARIANT_IN_ORDER "public open fun min(p0: kotlin.Int, p1: kotlin.Int): kotlin.Int defined in java.lang.Integer"
// EXPECT_VARIANT_IN_ORDER "public open fun min(p0: kotlin.Int, p1: kotlin.Int): kotlin.Int defined in java.lang.Math"
// EXPECT_VARIANT_IN_ORDER "public open fun min(p0: kotlin.Int, p1: kotlin.Int): kotlin.Int defined in java.lang.StrictMath"
// EXPECT_VARIANT_IN_ORDER "public open fun <T : (kotlin.Any..kotlin.Any?)> min(p0: (kotlin.collections.MutableCollection<out (T..T?)>..kotlin.collections.Collection<(T..T?)>?), p1: (java.util.Comparator<in (T..T?)>..java.util.Comparator<in (T..T?)>?)): (T..T?) defined in java.util.Collections"
// EXPECT_VARIANT_IN_ORDER "public open fun min(p0: kotlin.Double, p1: kotlin.Double): kotlin.Double defined in java.lang.Double"
// EXPECT_VARIANT_IN_ORDER "public open fun min(p0: kotlin.Float, p1: kotlin.Float): kotlin.Float defined in java.lang.Float"
// EXPECT_VARIANT_IN_ORDER "public open fun min(p0: kotlin.Long, p1: kotlin.Long): kotlin.Long defined in java.lang.Long"
package root

fun foo() {
    mi<caret>n(1, 2)
}
