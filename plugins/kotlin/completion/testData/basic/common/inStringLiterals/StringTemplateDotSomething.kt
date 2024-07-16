// FIR_IDENTICAL
// FIR_COMPARISON
// IGNORE_PLATFORM_COMMON_NATIVE+JVM: KTIJ-29848

fun foo(param: String) {
    val s = "$param.l<caret>bla-bla-bla"
}

// EXIST: { itemText: "length", attributes: "" }
// ABSENT: hashCode
// EXIST: { itemText: "lastIndex", attributes: "" }
