import kotlinx.coroutines.*

class SameFileThroughFunctions {

    suspend fun fun1() {
        fun2()
    }

    fun fun2() {
        fun3()
    }

    fun fun3() {
        runBlocking {  }
    }
}
