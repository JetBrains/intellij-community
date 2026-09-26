// IGNORE_K2_COPY
// IGNORE_K2_CUT
/* this test should pass once KTIJ-29859 is fixed */
package a

fun foo() {}
fun Int.ext() {}
fun Int.test() {
    <selection>ext()
    foo()</selection>
}