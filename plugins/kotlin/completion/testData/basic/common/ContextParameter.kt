interface Logger {
    fun log(message: String)
}

context(logger: Logger)
fun log() {
    logge<caret>
}

// EXIST: {"lookupString":"logger","typeText":"Logger","icon":"Parameter","attributes":"","allLookupStrings":"logger","itemText":"logger"}
// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K1