// "Replace usages of 'minBy((T) -> R) on Iterable<T>: T?' in whole project" "true"
// WITH_STDLIB
fun test() {
    listOf<Int>().minBy<caret> { it + 1 }
}
