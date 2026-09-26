// PROBLEM: none
import java.util.concurrent.Callable

fun main() {
    val j = J()
    j.<caret>setX(j::getX)
}
