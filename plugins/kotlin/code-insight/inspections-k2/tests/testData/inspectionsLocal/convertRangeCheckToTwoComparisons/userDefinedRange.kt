// PROBLEM: none
class MyInt(val value: Int) {
    infix operator fun rangeTo(other: MyInt) = MyIntRange(this.value, other.value)
}

class MyIntRange(val start: Int, val end: Int) {
    operator fun contains(item: MyInt) = item.value in start..end
}

fun boo(bar: MyInt){
    bar in MyInt(1) rangeTo MyInt(10)<caret>
}