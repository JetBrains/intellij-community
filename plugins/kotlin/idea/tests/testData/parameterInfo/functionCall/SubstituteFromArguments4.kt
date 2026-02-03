fun <T1, T2> f(p: Int, t: T1, pair: Pair<T1, T2>){}

fun test() {
    f(1, "", <caret>)
}

//currently: