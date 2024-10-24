// ENABLE_CALL_EXTENSIONS
// FIR_COMPARISON
// EXPECT_VARIANT_IN_ORDER "class my.test.pkg1.InterfaceType"
// EXPECT_VARIANT_IN_ORDER "class my.test.pkg2.InterfaceType"

package my.test.root

// extension is enabled, but call is not annotated, so we expect the default behavior
fun foo(bar: Interfa<caret>ceType) {
}
