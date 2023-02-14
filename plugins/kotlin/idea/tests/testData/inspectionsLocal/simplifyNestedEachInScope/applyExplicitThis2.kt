// PROBLEM: none
// WITH_STDLIB
val <T> List<T>.bar
    get() = 1

fun test(list: List<Int>) {
    list.apply<caret> {
        this.forEach {
            println(this.bar)
        }
    }
}