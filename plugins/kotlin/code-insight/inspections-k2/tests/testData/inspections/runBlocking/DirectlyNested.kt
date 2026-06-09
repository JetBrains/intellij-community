import kotlinx.coroutines.*

class DirectlyNested {
    fun fun1() {
        runBlocking {
            runBlocking {}
        }
    }

    suspend fun fun2() {
        runBlocking { }
    }

    fun fun3() {
        GlobalScope.launch {
            runBlocking {}
        }
    }

    suspend fun fun4() {
        coroutineScope {
            runBlocking { }
        }
    }

    suspend fun fun5() {
        coroutineScope {
            launch {
                runBlocking { }
            }
        }
    }

    fun fun6() {
        GlobalScope.async {
            runBlocking { }
        }
    }
}
