data class Box(val value: Int, val name: String, val active: Boolean)

data class Person(val name: String?)

fun foo(value: Int): Boolean {
    if (value % 2 == 0) {
        return true
    } else {
        return false
    }
}

fun bar(value: Int): Boolean {
    if (value % 2 == 0) return true else return false
}

fun baz(value: Int): Boolean {
    if (value % 2 == 0) return value > 10 else return false
}

fun qux(value: Int): Boolean {
    if (value % 2 == 0) return true else return value > 10
}

fun quux(value: Int): Boolean {
    if (value % 2 == 0) return value > 10 else return true
}

fun withFlag(value: Int, flag: Boolean): Boolean {
    return if (value % 2 == 0 || flag) !flag else false
}

fun withIs(value: Any?, box: Box): Boolean {
    return if (value is String) true else box.name != "fallback"
}

fun withIn(value: Int, box: Box): Boolean {
    return if (value in 1..10) box.value == 0 else true
}

fun withObjectFields(left: Box, right: Box): Boolean {
    return if (left.active) left.value == right.value else false
}

fun withNull(person: Person, value: Int): Boolean {
    return if (person.name != null) true else value == 0
}

fun noChange(value: Int, flag: Boolean?): Boolean? {
    return if (value % 2 == 0) flag else false
}

fun noChangeCall(value: Int): Boolean {
    return if (value % 2 == 0) checkValue(value) else false
}

fun noChangeCallCondition(value: Int): Boolean {
    return if (checkValue(value)) value > 10 else false
}

fun noChangeObjectFieldCondition(person1: Person, person2: Person): Boolean {
    if (person1.name != person2.name) return false
    return true
}

fun checkValue(value: Int): Boolean = value > 10
