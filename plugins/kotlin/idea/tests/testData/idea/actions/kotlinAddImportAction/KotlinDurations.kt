// IGNORE_K2
// WITH_STDLIB
// EXPECT_VARIANT_IN_ORDER "public final val kotlin.Int.seconds: kotlin.time.Duration defined in kotlin.time.Duration.Companion"
package root

fun foo() {
    val x = 1.seconds<caret>
}
