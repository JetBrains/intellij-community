class Some {
    fun some(): Some? = this
}

public fun bar(): String? = Some()?.some()
    <caret>
    ?.some()
    ?.some()

// IGNORE_FORMATTER
// SET_FALSE: CONTINUATION_INDENT_FOR_CHAINED_CALLS