class Logger {
    fun log(){}
}

abstract class WithLogging {
    val logger = Logger()
}

class CX827 {
    companion object: WithLogging()

    fun foo() {
        logger.log()
    }
}

fun main(args: Array<String>) {
    println(args)
    CX827()
}
