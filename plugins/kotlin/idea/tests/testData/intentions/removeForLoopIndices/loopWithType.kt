// WITH_STDLIB
fun foo(bar: List<Int>) {
    for ((i<caret> : Int, b: Int) in bar.withIndex()) {

    }
}