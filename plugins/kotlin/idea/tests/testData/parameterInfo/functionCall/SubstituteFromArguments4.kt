fun <T1, T2> f(p: Int, t: T1, pair: Pair<T1, T2>){}

fun test() {
    f(1, "", <caret>)
}

//currently:
/*
Text_K1: Text: (p: Int, t: String, <highlight>pair: Pair<T1, T2></highlight>), Disabled: false, Strikeout: false, Green: true
Text_K2: Text: (p: Int, t: String, <highlight>pair: Pair<String, T2></highlight>), Disabled: false, Strikeout: false, Green: true
*/
