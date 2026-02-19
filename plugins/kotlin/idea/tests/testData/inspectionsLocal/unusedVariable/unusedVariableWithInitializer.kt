// "Remove variable 'a' (may change semantics)" "true"
var cnt = 5
fun getCnt() = cnt++
fun f() {
    var <caret>a = getCnt()
}
