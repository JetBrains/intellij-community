// @ParameterName annotation is ignored when not used in function type
fun f(pickMe: @ParameterName("notMe") String) {
    f(<caret>)
}

/*
Text: (<highlight>pickMe: String</highlight>), Disabled: false, Strikeout: false, Green: true
*/
