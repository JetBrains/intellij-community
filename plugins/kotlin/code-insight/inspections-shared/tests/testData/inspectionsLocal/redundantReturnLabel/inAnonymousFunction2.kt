// PROBLEM: none
inline fun foo(f: (String?) -> Int): Int = f("")

fun test(): Int {
    return foo(fun(it: String?): Int {
        if (it != null) return@test<caret> 1
        return 0
    })
}