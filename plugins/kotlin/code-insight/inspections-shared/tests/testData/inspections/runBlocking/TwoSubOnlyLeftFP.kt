package virtualFunctions.twoSubOnlyLeftFP

import kotlinx.coroutines.runBlocking

interface Interf {
    fun bar()
}

class SubClassRight: Interf {
    override fun bar() {
    }
}

class SubClassLeft: Interf {
    override fun bar() {
        runBlocking {  }
    }
}

fun main() {
    val aa: Interf = SubClassRight()
    runBlocking {
        aa.bar()
    }
}

