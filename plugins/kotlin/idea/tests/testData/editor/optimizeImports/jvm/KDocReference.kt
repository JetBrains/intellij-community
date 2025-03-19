// TODO: it is quite similar to KDocRefernece.kt for K2 except no default imported ArrayList, see KTIJ-26751
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write
import java.util.ArrayList
import java.util.concurrent.locks.Lock

/**
 * Reference to [ArrayList]
 * Reference to [fixedRateTimer]
 * Reference to [kotlin.concurrent.read]
 * Reference to [Lock.withLock]
 * Reference to [java.util.concurrent.locks.ReentrantReadWriteLock.write]
 */
fun foo() {
}
