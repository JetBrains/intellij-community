// WITH_STDLIB
fun qualifierDoesNotAddSmartCast(x: Any) {
    x as String
    if (x.length == 0) return
    // x participates in condition and smartcast on x is present but smartcast is induced earlier
    if (<warning descr="Condition 'x.length > 0' is always true">x.length > 0</warning>) {
        println(x.trim())
    }
}

fun qualifierDoesNotAddSmartCastNullity(x: String?) {
    x!!
    if (x.length == 0) return
    // x participates in condition and smartcast on x is present but smartcast is induced earlier
    if (<warning descr="Condition 'x.length > 0' is always true">x.length > 0</warning>) {
        println(x.trim())
    }
}

fun branchDisappears(x: String?) {
    val b = x != null
    if (b) return
    if (<warning descr="Condition 'x == null' is always true">x == null</warning>) {
        println(123)
    } else {
        println(x.trim())
    }
}

fun nullity(x: Any?) {
    val b = x == null
    if (!b) {
        if (x != null) {
            simple(x) // smartcast necessary
        }
        if (<warning descr="Condition 'x != null' is always true">x != null</warning>) {
            println(x) // smartcast unnecessary
        }
    }
}

fun simple(x: Any) {
    val b = x is String
    if (b) {
        // Do not report: necessary for smart cast
        if (x is String) {
            println(x.trim())
        }
    }
}

fun unsafeVariance(list: List<String>, value: String?) {
    val b = value != null
    if (b) {
        // TODO: We suppress warning here, as Kotlin reports that list.contains(value) has a smartcast
        //       but it's possible to call it without null-check (related to @UnsafeVariance?)
        if (value != null && list.contains(value)) {}
        if (list.contains(value)) {}
    }
}

fun tooWeakVariableType(a: B) {
    val b = a is C
    if (b) {
        // TODO: we suppress here as Kotlin reports the smartcast but
        //       smartcast is unnecessary, as b() is declared in B supertype as well
        if (<warning descr="Condition 'a is C' is always true">a is C</warning>) {
            a.b()
        }
        a.b()
    }
}

fun equality(a: A, b: B) {
    val x = a === b
    if (x) {
        if (<warning descr="Condition 'a === b' is always true">a === b</warning>) a.a()
        if (a === b) a.b()
        if (a === b) b.a()
        if (<warning descr="Condition 'a === b' is always true">a === b</warning>) b.b()
    }
}

interface A {
    fun a()
}

interface B {
    fun b()
}

interface C : B {
    override fun b()
}

fun doubleIf(x: String?) {
    var y : String? = null
    if (x != null) {
        y = x.trim()
    }
    if (x == null || y == null) return
    println(y.trim())
}

fun testWhen(x: String?) {
    val b = x == null
    if (b) {
        when {
            <warning descr="Condition 'x != null' is always false">x != null</warning> -> {
                println(x.trim())
            }
        }
        return
    }
    when {
        x != null -> {
            println(x.trim())
        }
    }
}

fun negatedTypeTest(x: Any) {
    val b = x is String
    if (!b) return
    if (x !is String) return
    x.trim()
}