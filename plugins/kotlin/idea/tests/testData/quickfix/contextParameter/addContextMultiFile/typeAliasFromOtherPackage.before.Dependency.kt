package lib

class Logger {
    fun log(message: String) {}
}

typealias Log = Logger

context(log: Log)
fun logged() {}