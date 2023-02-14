package a

import a.Outer.*

class Outer {
    inner class Inner {
    }
    inner class Inner2 {
    }
    class Nested {
    }
    enum class NestedEnum {
    }
    object NestedObj {
    }
    interface NestedInterface {
    }
    annotation class NestedAnnotation
}

<selection>fun f(i: Inner, n: Nested, e: NestedEnum, o: NestedObj, t: NestedInterface, aa: NestedAnnotation) {
    Outer().Inner2()
}</selection>