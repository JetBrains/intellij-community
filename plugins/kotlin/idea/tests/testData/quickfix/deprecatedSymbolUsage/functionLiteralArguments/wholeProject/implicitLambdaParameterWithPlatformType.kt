// "Replace usages of 'maxBy((T) -> R) on Iterable<T>: T?' in whole project" "true"
// WITH_RUNTIME
import java.util.Collections

fun test() {
    val list = Collections.singletonList("a")
    list.maxBy<caret> { it.length }
}