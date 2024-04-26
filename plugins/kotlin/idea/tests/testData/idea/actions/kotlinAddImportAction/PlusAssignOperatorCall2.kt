// IGNORE_K2
// EXPECT_VARIANT_IN_ORDER "public operator fun <T> pack.A<T>.plusAssign(element: T): kotlin.Unit defined in pack in file PlusAssignOperatorCall2.dependency.kt"
// WITH_STDLIB
package other

import pack.Arr
fun foo(arr: Arr<Int>) {
    arr.plusA<caret>ssign(21)
}