fun <T1, T2> f(p: Int, t1: T1, t2: T2){}

fun test() {
    f(<caret>1, "", 1)
}
