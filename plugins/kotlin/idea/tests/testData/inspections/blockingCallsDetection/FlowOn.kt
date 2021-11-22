import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*


fun flowOnMain(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(100)
        emit(i)
    }
}.flowOn(Dispatchers.Main)

fun flowSimple(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(100)
        emit(i)
    }
}

fun flowOnIO(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.sleep(100)
        emit(i)
    }
}.flowOn(Dispatchers.IO)

fun flowOnIOAndMap(): Flow<Unit> = flow {
    for (i in 1..3) {
        Thread.sleep(100)
        emit(i)
    }
}.map { Thread.sleep(100) }
    .flowOn(Dispatchers.IO)
