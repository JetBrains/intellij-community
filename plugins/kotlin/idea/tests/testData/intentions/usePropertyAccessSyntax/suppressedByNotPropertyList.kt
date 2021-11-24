// WITH_STDLIB
// WITH_JDK
// IS_APPLICABLE: false
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    val i = AtomicInteger()
    val value = i.getAndIncrement()<caret>
}