// Expect that root.p.bar(arg: String) and root.q.bar(arg: Long) are in the import list, since they're the only non-deprecated overload
// in each package. (Note that no overloads have zero arguments, so none match this function call exactly.)

// EXPECT_VARIANT_IN_ORDER "public fun bar(arg: kotlin.String): kotlin.Unit defined in root.p"
// EXPECT_VARIANT_IN_ORDER "public fun bar(arg: kotlin.Long): kotlin.Unit defined in root.q"

// EXPECT_VARIANT_NOT_PRESENT "public fun bar(arg: kotlin.Int): kotlin.Unit defined in root.p"
// EXPECT_VARIANT_NOT_PRESENT "public fun bar(arg: kotlin.Long): kotlin.Unit defined in root.p"
// EXPECT_VARIANT_NOT_PRESENT "public fun bar(arg: kotlin.Double): kotlin.Unit defined in root.p"
// EXPECT_VARIANT_NOT_PRESENT "public fun bar(arg: kotlin.Int): kotlin.Unit defined in root.q"
// EXPECT_VARIANT_NOT_PRESENT "public fun bar(arg: kotlin.String): kotlin.Unit defined in root.q"
// EXPECT_VARIANT_NOT_PRESENT "public fun bar(arg: kotlin.Double): kotlin.Unit defined in root.q"

package root

fun foo() {
    ba<caret>r()
}
