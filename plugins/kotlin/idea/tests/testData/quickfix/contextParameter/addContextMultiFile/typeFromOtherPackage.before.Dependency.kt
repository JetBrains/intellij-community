package lib

class Logger {
    fun log(message: String) {}
}

context(logger: Logger)
fun logged() {}