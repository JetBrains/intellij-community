// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package some.long.pkg

interface WithInvoke {
    operator fun invoke() {}

    operator fun plus(i: Int)
}

val topLevelProp: Any = Any()

fun test() {
    if (topLevelProp is WithInvoke) {

        some.long.pkg.topLevelProp.invoke()

        some.long.pkg.topLevelProp()

        val value: WithInvoke = some.long.pkg.topLevelProp

        val value2: WithInvoke = (some.long.pkg.topLevelProp)

        val value3: WithInvoke = ((some.long.pkg.topLevelProp))

        some.long.pkg.topLevelProp + 1

        (some.long.pkg.topLevelProp) + 1

        ((some.long.pkg.topLevelProp)) + 1
    }
}

data class WithProp(val prop: Any?)

fun test2(param: WithProp?) {
    if (param?.prop is WithInvoke?) {

        param?.prop?.invoke()

        param?.prop?.plus(10)

    }
}