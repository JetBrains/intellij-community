// EXPECT_VARIANT_IN_ORDER "public operator fun java.time.Instant.plus(period: pack.TradingPeriod): java.time.Instant defined in pack in file PlusOperator1.dependency.kt"
// RUNTIME_WITH_FULL_JDK
package other

import pack.TradingPeriod
import java.time.Instant

fun test(instant: Instant, period: TradingPeriod) {
    instant + <caret>period
}