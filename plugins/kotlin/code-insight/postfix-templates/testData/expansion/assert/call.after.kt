import Processor.shouldProcess

fun test() {
    assert(shouldProcess())
}

object Processor {
    fun shouldProcess(): Boolean = true
}