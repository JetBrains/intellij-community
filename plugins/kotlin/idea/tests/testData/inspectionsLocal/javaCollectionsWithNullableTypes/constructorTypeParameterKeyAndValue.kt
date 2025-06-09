// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentSkipListMap' is parameterized with nullable types
import java.util.concurrent.ConcurrentSkipListMap

val map = ConcurrentSkipListMap<String?, <caret>String?>()