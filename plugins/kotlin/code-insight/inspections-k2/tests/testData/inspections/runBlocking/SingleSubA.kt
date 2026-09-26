package virtualFunctions.singlesubA

import kotlinx.coroutines.runBlocking

interface Interf {
    fun bar()
}

class SubClass: Interf {
    override fun bar() {
        runBlocking {  }
    }
}

fun main() {
    val aa: Interf = SubClass()
    runBlocking {
        aa.bar()
    }
}
