// IGNORE_K2
interface Receiver {
    fun receiverMember()
    val receiverProp: Int
}

open class Base(value: Receiver.() -> Any) {
    class Nested
}

class A : Base({ <caret> }) {
    fun member() {}
    val prop: Int = 10
}

// EXIST: Nested
// EXIST: receiverMember
// EXIST: receiverProp
// EXIST: {"lookupString":"this","typeText":"Receiver","attributes":"bold","allLookupStrings":"this","itemText":"this"}
// ABSENT: member
// ABSENT: prop
