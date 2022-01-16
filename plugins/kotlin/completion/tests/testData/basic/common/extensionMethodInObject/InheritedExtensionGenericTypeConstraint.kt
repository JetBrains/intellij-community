open class A {
    fun <T : CharSequence> T.fooExtCharSequence() {}
    fun <T : Number> T.fooExtNumber() {}
    fun <T> T.fooExtAny() {}
}

object AOBject : A()

fun usage() {
    10.fooE<caret>
}

// EXIST: { lookupString: "fooExtNumber", itemText: "fooExtNumber" }
// EXIST: { lookupString: "fooExtAny", itemText: "fooExtAny" }
// ABSENT: { lookupString: "fooExtCharSequence", itemText: "fooExtCharSequence" }
