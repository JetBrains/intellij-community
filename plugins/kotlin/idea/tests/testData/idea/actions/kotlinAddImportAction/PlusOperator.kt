// EXPECT_VARIANT_IN_ORDER "public operator fun pack.Instant.plus(period: pack.TradingPeriod): pack.Instant defined in pack in file PlusOperator.dependency.kt"
package other

import pack.TradingPeriod
import pack.Instant

fun test(instant: Instant, period: TradingPeriod) {
    instant +<caret> period
}