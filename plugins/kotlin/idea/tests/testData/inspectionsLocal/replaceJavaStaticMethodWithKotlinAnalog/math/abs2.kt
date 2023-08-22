// WITH_STDLIB
import java.lang.Math.abs

fun x() {
    listOf<Int>()
        .take(10)
        .filter { <caret>abs(it) < 10 }
}