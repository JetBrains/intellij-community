// PROBLEM: none

annotation class InfoMarker<caret>(val info: String)
fun test() {
    InfoMarker("")
}