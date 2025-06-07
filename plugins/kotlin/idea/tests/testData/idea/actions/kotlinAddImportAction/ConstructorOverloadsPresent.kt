// EXPECT_VARIANT_IN_ORDER "class root.p.Bar"
// EXPECT_VARIANT_IN_ORDER "class root.q.Bar"

package root

fun foo() {
    Ba<caret>r()
}
