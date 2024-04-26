// PROBLEM: none
// MIN_JAVA_VERSION: 9
// WITH_STDLIB
import java.util.concurrent.atomic.AtomicLong

fun main() {
    val al = AtomicLong()
    al.<caret>getAcquire()
}