// EXPECT_VARIANT_IN_ORDER "public fun bar(p: kotlin.String): kotlin.Unit defined in my.test.pkg1 in file NoExtension.dependency1.kt"
// EXPECT_VARIANT_IN_ORDER "public fun bar(p: kotlin.String): kotlin.Unit defined in my.test.pkg2 in file NoExtension.dependency2.kt"
package my.test.root

// no extension specified, this is on purpose to test the default behaviour
import my.test.MyAnnotation

@MyAnnotation
fun foo() {
    bar<caret>("a")
}
