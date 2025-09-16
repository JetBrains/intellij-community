// PROBLEM: none
// WITH_STDLIB
class Clazz: Comparable<String> by <caret>("hello".filter { it != 'l' })
