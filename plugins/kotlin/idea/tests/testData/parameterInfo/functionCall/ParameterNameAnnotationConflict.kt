// @ParameterName annotation takes precedence over name in function type parameter
fun <T> T.foo(): (notMe: @ParameterName("pickMe") T) -> Unit{}

fun f() {
    val v = "a".foo()
    v(<caret>)
}

