package virtualFunctions.subSubOnlyFirstFP

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
    val aa: Interf = SubClass()
    runBlocking {
        aa.bar()
    }
}
