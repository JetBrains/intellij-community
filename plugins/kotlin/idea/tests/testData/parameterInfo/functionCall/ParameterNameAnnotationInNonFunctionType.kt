// @ParameterName annotation is ignored when not used in function type
fun f(pickMe: @ParameterName("notMe") String) {
    f(<caret>)
}

