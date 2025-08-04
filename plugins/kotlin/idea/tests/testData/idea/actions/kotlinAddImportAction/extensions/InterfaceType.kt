// ENABLE_CALL_EXTENSIONS
// FIR_COMPARISON
// EXPECT_VARIANT_IN_ORDER "class my.test.pkg2.InterfaceType"
// EXPECT_VARIANT_IN_ORDER "class my.test.pkg1.InterfaceType"

package my.test.root

// extension is enabled, so we expect the annotated method to be prioritized
import my.test.MyAnnotation

@MyAnnotation
fun foo(bar: Interfa<caret>ceType) {
}
