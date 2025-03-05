// EXPECT_VARIANT_IN_ORDER "public fun kotlin.String.quoteIfNeeded(): kotlin.Unit defined in p1 in file Flexible.dependency.kt"
package root

import java.util.List

fun foo(a: List<String>) {
    a[0].quoteIf<caret>Needed()
}
