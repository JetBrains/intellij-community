// EXPECT_VARIANT_IN_ORDER "class apples.Apple"
package root

import appleExtensions.Apple

fun createApples() {
    Apple("super big")
    Apple(1<caret>)
}
