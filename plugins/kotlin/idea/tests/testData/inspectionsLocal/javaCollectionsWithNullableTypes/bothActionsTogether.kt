// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentSkipListMap' is parameterized with nullable types
import java.util.concurrent.ConcurrentSkipListMap

class MyMap<T> {
    val map = ConcurrentSkipListMap<T,<caret> String?>()
}