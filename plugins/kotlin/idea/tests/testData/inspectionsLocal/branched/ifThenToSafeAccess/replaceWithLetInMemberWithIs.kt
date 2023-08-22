// HIGHLIGHT: INFORMATION
// WITH_STDLIB

class Test {
    fun doAThing(param1: String) {

    }

    fun doAThingIfPresent(param1: String?) {
        <caret>if (param1 is String) {
            doAThing(param1)
        }
    }
}