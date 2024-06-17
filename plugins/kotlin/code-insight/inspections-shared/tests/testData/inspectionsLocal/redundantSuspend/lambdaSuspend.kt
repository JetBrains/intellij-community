fun myRunSuspendLambda(action: suspend () -> Unit) {}

suspend<caret> fun test(action: suspend () -> Unit) {

    myRunSuspendLambda {
        action()
    }

}
