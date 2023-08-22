// WITH_STDLIB

fun foo() {
    x(1) {<caret> ::y }
}

fun y() {

}

fun x(number: Int, func: () -> Unit) {

}