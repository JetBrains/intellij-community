// NAME_COUNT_TO_USE_STAR_IMPORT: 2
package a

fun Int.ext1() {}
fun Int.ext2() {}

fun Int.test() {
    <selection>ext1()
    ext2()</selection>
}
