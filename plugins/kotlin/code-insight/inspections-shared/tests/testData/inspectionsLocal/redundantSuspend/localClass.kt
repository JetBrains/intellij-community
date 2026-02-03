suspend<caret> fun test(action: suspend () -> Unit) {

    class LocalClass {
        suspend fun inLocal() {
            action()
        }
    }

}

