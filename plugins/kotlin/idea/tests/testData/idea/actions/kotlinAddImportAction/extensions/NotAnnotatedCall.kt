// ENABLE_CALL_EXTENSIONS
// EXPECT_VARIANT_IN_ORDER "public fun bar(p: kotlin.String): kotlin.Unit defined in my.test.pkg1 in file NotAnnotatedCall.dependency1.kt"
// EXPECT_VARIANT_IN_ORDER "public fun bar(p: kotlin.String): kotlin.Unit defined in my.test.pkg2 in file NotAnnotatedCall.dependency2.kt"
package my.test.root

// extension is enabled, but call is not annotated, so we expect the default behavior
import my.test.MyAnnotation

@MyAnnotation
fun foo() {
    bar<caret>("a")
}
