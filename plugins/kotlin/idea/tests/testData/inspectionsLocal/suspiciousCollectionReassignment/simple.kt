// FIX: Change type to mutable
// WITH_STDLIB
fun test() {
    var list = listOf(1)
    list <selection>+=<caret></selection> 2
}
// OFFLINE_REPORT: "'+=' creates new list under the hood"