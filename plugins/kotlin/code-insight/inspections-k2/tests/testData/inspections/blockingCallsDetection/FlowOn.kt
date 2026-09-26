// CONSIDER_UNKNOWN_AS_BLOCKING: false
// CONSIDER_SUSPEND_CONTEXT_NON_BLOCKING: true
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.lang.Thread

fun flowOnMain(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1)
        emit(i)
    }
}.flowOn(Dispatchers.Main)

fun flowSimple(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(2)
        emit(i)
    }
}

fun flowOnIO(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.sleep(3)
        emit(i)
    }
}.flowOn(Dispatchers.IO)

fun flowOnIOAndMap(): Flow<Unit> = flow {
    for (i in 1..3) {
        Thread.sleep(4)
        emit(i)
    }
}.map { Thread.sleep(5) }
    .flowOn(Dispatchers.IO)
