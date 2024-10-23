// ENABLE_CALL_EXTENSIONS
// FIR_COMPARISON
// EXPECT_VARIANT_IN_ORDER "class my.test.pkg2.MyClass"
// EXPECT_VARIANT_IN_ORDER "class my.test.pkg1.MyClass"

package my.test.root

// extension is enabled, so we expect the annotated method to be prioritized
import my.test.MyAnnotation

@MyAnnotation
fun foo() {
    val bar = MyCl<caret>ass()
}
