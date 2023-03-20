package one.two

class KotlinClass {
    companion object Named {
        object NestedObject {
            @JvmStatic
            fun staticFunc<caret>tion() {

            }
        }
    }
}

class Receiver