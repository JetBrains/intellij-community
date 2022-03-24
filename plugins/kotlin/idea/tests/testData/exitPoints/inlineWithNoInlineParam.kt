fun f(a: Int): Int {
    if (a < 5) {
        run1(fun (): Int {
            <caret>return 1
        })
    }
    return 2
}

inline public fun <T> run1(noinline f: () -> T): T { }

//HIGHLIGHTED: fun
//HIGHLIGHTED: return 1
