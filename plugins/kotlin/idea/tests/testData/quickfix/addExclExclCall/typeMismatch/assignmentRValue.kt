// "Add non-null asserted (!!) call" "true"
fun test(s: String?) {
    var z: String = ""
    z = <caret>s
}