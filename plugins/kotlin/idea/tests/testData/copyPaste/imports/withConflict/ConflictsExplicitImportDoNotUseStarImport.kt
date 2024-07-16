// NAME_COUNT_TO_USE_STAR_IMPORT: 2
// IGNORE_K1_COPY
// IGNORE_K1_CUT
package a

fun foo() {}
fun Int.ext1() {}
fun Int.ext2() {}

fun Int.test() {
    <selection>foo()
    ext1()
    ext2()</selection>
}
