package a

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

<selection>fun f(i: Outer.Inner, n: Outer.Nested, e: Outer.NestedEnum, o: Outer.NestedObj, t: Outer.NestedInterface, aa: Outer.NestedAnnotation) {
    Outer().Inner2()
}</selection>