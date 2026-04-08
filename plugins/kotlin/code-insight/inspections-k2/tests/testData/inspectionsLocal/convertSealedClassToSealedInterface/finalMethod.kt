// PROBLEM: none
interface Base {
    fun process()
}

sealed class Result<caret> : Base {
    final override fun process() = println("done")
}

class Success : Result()