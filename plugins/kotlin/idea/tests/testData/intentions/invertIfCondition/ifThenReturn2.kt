fun foo() {
    val x = 2
    <caret>if (x <= 1) {
        bar1()
        bar2()
        return
    }
    bar1()
}

fun bar1(){}
fun bar2(){}
