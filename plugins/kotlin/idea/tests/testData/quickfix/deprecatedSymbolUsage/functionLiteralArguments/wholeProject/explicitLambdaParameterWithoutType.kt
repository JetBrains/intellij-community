// "Replace usages of 'minBy((T) -> R) on Iterable<T>: T?' in whole project" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.5
fun test() {
    listOf<Int>().minBy<caret> { i -> i + 1 }
}
