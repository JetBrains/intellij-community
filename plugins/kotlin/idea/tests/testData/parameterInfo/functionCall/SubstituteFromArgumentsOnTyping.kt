fun <T> f(t1: T, t2: T){}

fun test() {
    f(<caret>)
}

// TYPE: "1"