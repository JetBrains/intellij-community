// IS_APPLICABLE: false

import MemberPropertyInRoot.test

object MemberPropertyInRoot {
    val test: Int = 42
}

fun check() {
    MemberPropertyInRoot::<caret>test
}
