// IGNORE_K1
// EXPECT_VARIANT_IN_ORDER "public operator fun foo.Foo.getValue(thisRef: kotlin.Any?, property: kotlin.Any?): kotlin.Int defined in foo in file operatorGetValueAndSetValueExtension.dependency.kt"
// EXPECT_VARIANT_IN_ORDER "public operator fun foo.Foo.setValue(thisRef: kotlin.Any?, property: kotlin.Any?, value: kotlin.Int): kotlin.Unit defined in foo in file operatorGetValueAndSetValueExtension.dependency.kt"

import foo.Foo

fun main() {
    var x b<caret>y Foo()
}