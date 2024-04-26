interface I {
    fun takeXxx(): Int = 0
    fun takeYyy(): Int = 0
    fun takeZzz(): Int = 0
}

fun takeYyy(i: I) = i.take<caret>

// IGNORE_K2
// ORDER: takeYyy
// ORDER: takeXxx
// ORDER: takeZzz
