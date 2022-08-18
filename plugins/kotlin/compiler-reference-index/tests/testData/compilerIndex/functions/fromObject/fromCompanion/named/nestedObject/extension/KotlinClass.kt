package one.two

class KotlinClass {
    companion object Named {
        object NestedObject {
            fun Receiver.ext<caret>ension(i: Int) {

            }
        }
    }
}

class Receiver