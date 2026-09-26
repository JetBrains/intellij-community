interface I1 {
    val <caret>s: String
}

class A1(override val s: String): I1
class B1(override val s: String): I1
