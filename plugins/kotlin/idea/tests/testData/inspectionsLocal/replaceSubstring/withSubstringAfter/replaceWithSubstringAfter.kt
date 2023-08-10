// WITH_STDLIB

fun foo(s: String) {
    s.substring<caret>(s.indexOf('x'))
}