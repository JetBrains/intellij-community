// WITH_STDLIB

fun foo(s: String) {
    s.substring<caret>(0, s.length - 5);
}