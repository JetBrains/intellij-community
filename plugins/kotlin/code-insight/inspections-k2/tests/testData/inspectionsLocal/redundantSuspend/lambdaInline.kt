// PROBLEM: none
inline fun myRunInline(action: () -> Unit) {}

suspend<caret> fun test(action: suspend () -> Unit) {

    myRunInline {
        action()
    }

}
