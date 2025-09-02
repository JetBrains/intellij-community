// WITH_STDLIB

fun CharSequence.substringAfterLast1(delimeter: Char): Char = TODO()

fun foo(s: String) {
    s.substring<caret>(0, 10).
        substringAfterLast1(',')
}