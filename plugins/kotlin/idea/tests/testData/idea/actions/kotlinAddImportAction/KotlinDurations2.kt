// WITH_STDLIB
// EXPECT_VARIANT_IN_ORDER "@kotlin.internal.InlineOnly public final val kotlin.Int.seconds: kotlin.time.Duration defined in kotlin.time.Duration.Companion"
// EXPECT_VARIANT_IN_ORDER "@kotlin.Deprecated @kotlin.DeprecatedSinceKotlin @kotlin.SinceKotlin @kotlin.time.ExperimentalTime public val kotlin.Int.seconds: kotlin.time.Duration defined in kotlin.time"
package root

fun foo(x: Int?) = x.seconds<caret>
