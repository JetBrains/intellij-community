package ppp

interface I

fun foo() {
    class C : I

    fun Any.xxx() = 1
    fun C.xxx() = 1

    val c = C()
    c.xx<caret>
}

val Any.xxx: Int get() = 1
val I.xxx: Int get() = 1


// EXIST: {"lookupString":"xxx","tailText":"() for C","typeText":"Int","icon":"Function"}
// EXIST: {"lookupString":"xxx","tailText":" for I in ppp","typeText":"Int","icon":"org/jetbrains/kotlin/idea/icons/field_value.svg"}
// NOTHING_ELSE
