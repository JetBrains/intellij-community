import MemberPropertyInRoot.test

object MemberPropertyInRoot {
    val test: Int = 42
}

fun check() {
    ::<caret>test
}
