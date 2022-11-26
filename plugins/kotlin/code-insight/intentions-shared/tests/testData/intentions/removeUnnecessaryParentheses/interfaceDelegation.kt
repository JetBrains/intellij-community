// IS_APPLICABLE: false
// WITH_STDLIB
class Clazz: Comparable<String> by <caret>("hello".filter { it != 'l' })
