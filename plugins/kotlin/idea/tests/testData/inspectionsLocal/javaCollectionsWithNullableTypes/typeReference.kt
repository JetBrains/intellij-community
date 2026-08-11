// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentLinkedQueue' is parameterized with a nullable type
// K2_AFTER_ERROR: INITIALIZER_TYPE_MISMATCH
import java.util.concurrent.ConcurrentLinkedQueue

val queue = ConcurrentLinkedQueue<String?>()
val queue2: ConcurrentLinkedQueue<String?<caret>> = queue