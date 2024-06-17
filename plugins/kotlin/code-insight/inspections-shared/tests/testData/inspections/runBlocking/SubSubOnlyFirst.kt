package virtualFunctions.subSubOnlyFirst

import kotlinx.coroutines.runBlocking

interface Interf {
    fun bar()
}

open class SubClass: Interf {
    override fun bar() {

    }
}

class SubSubClass: SubClass() {
    override fun bar() {
        runBlocking {  }
    }
}

fun main() {
    val aa: Interf = SubSubClass()
    runBlocking {
        aa.bar()
    }
}
