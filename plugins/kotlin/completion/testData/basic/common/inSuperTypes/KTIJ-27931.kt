// FIR_IDENTICAL

sealed interface Command<P, R> {
    operator fun invoke(parameter: P): R
}

data object DoNothingCommand : Command<Any, Nothing> {

    override fun invoke(parameter: Any): Nothing =
        throw RuntimeException()
}

fun <P : Any, R> invoke(function: (P) -> R = DoNothing<caret>Command::invoke) {}

// EXIST: DoNothingCommand
// NOTHING_ELSE