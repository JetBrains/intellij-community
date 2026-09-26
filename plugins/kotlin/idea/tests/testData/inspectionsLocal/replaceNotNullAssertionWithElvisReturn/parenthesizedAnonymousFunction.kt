

fun test(): Any {
    return (fun(a: Any?) {
        a!!<caret>
    })
}