// IGNORE_FIR
// FIR renders "pair: Pair<String, ERROR_TYPE <Cannot infer argument for type parameter T2>>"
// ConeTypeIdeRenderer does not properly render ConeSimpleDiagnostic(kind = CannotInferParameterType); new ConeDiagnostic needed
fun <T1, T2> f(p: Int, t: T1, pair: Pair<T1, T2>){}

fun test() {
    f(1, "", <caret>)
}

//TODO:
/*
Text: (p: Int, t: String, <highlight>pair: Pair<String, T2></highlight>), Disabled: false, Strikeout: false, Green: true
*/

//currently:
/*
Text: (p: Int, t: String, <highlight>pair: Pair<T1, T2></highlight>), Disabled: false, Strikeout: false, Green: true
*/
