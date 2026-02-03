package one.two

class KotlinClass {
    companion object Named {
        object NestedObject {
            @JvmStatic
            fun Receiver.staticExt<caret>ension(i: Int) {

            }
        }
    }
}

class Receiver