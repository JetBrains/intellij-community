package a

class Test {
    companion object {
        suspend fun suspendMethod(): Unit = Unit
        fun nonSuspendMethod(): Unit = Unit
    }
}
