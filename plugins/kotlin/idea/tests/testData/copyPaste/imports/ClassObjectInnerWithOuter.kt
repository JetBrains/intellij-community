package a

import a.Outer.Companion.Nested
import a.Outer.Companion.NestedEnum
import a.Outer.Companion.NestedObj
import a.Outer.Companion.NestedInterface
import a.Outer.Companion.NestedAnnotation

class Outer {
    companion object {
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
}

<selection>fun f(n: Outer.Companion.Nested, e: Outer.Companion.NestedEnum, o: Outer.Companion.NestedObj, t: Outer.Companion.NestedInterface, a: Outer.Companion.NestedAnnotation) {
}</selection>