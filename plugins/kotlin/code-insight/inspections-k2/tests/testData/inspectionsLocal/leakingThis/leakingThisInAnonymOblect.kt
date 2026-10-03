// PROBLEM: Leaking this
// FIX: none
class Foo1 {
    class Foo2 {
        init {
            var a = Any()
            val c = object {
                init {
                    a = <caret>this
                }
            }
        }
    }
}
