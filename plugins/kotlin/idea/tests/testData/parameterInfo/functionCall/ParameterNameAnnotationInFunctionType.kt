fun <T> T.foo(): Function1<@ParameterName("item") T, @ParameterName("item") Unit>

fun f() {
    val v = "a".foo()
    v(<caret>)
}

