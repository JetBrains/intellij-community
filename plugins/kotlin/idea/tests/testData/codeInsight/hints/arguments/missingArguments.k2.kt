class Receiver {
    fun member(first: String, second: Int) {}
}

fun required(first: String, second: Int, third: Boolean = false) {}
fun withVararg(first: String, vararg rest: String, second: Int) {}
fun overloaded(first: String, second: Int) {}
fun overloaded(first: String, second: Int, third: Boolean = false) {}

fun test(receiver: Receiver) {
    val first = "value"
    required(/*<# [missingArguments.kt:79]first| = TODO()|, |[missingArguments.kt:94]second| = TODO() #>*/)
    required(first/*<# , |[missingArguments.kt:94]second| = TODO() #>*/)
    required(second = 1/*<# , |[missingArguments.kt:79]first| = TODO() #>*/)
    withVararg(/*<# [missingArguments.kt:149]first| = TODO()|, |[missingArguments.kt:185]second| = TODO() #>*/)
    receiver.member(/*<# [missingArguments.kt:32]first| = TODO()|, |[missingArguments.kt:47]second| = TODO() #>*/)
    overloaded(/*<# [missingArguments.kt:216]first| = TODO()|, |[missingArguments.kt:231]second| = TODO() #>*/)
}
