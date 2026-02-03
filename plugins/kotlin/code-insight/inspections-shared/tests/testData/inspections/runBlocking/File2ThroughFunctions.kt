import kotlinx.coroutines.*

class File2ThroughFunctions {
    fun fun3() {
        runBlocking {
            fun2()
        }
    }

    fun fun2() {
        File1ThroughFunctions().fun1()
    }
}
