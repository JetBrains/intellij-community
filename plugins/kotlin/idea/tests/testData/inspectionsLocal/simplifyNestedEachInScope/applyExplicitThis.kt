// PROBLEM: none
// WITH_STDLIB
class Test<T>(list: List<T>) : List<T> by list {
    fun print(item: T) {
        println(item)
    }
}

fun test() {
    Test(listOf(1, 2, 3)).apply<caret> {
        this.forEach {
            this.print(it)
        }
    }
}