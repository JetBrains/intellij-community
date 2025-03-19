package virtualFunctions.singlesubB

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
    val aa: SubClass = SubClass()
    runBlocking {
        aa.bar()
    }
}
