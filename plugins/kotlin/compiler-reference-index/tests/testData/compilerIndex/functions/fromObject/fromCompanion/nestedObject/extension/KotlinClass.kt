package one.two

class KotlinClass {
    companion object {
        object NestedObject {
            fun Receiver.ext<caret>ension(i: Int) {

            }
        }
    }
}

class Receiver