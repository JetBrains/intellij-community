// EXPECT_VARIANT_IN_ORDER "public fun bar(): kotlin.Unit defined in root.q"
// EXPECT_VARIANT_IN_ORDER "public fun bar(): kotlin.Unit defined in root.r"
// EXPECT_VARIANT_IN_ORDER "public fun bar(): kotlin.Unit defined in root.p"

package root

fun foo() {
    // Expect that "root.q.bar" is imported since it is the first alphabetically that is not deprecated.
    ba<caret>r()
}
