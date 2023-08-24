// FIR_IDENTICAL

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">refs</symbolName>() {
    var <warning descr="[ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE] Variable 'a' is assigned but never accessed" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE">a</symbolName></symbolName></warning> = 1
    val <warning descr="[UNUSED_VARIABLE] Variable 'v' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">v</symbolName></warning> = {
      <symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName descr="Wrapped into a reference object to be modified when captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">a</symbolName></symbolName> = 2
    }

    var <warning descr="[ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE] Variable 'x' is assigned but never accessed" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName></warning> = 1
    val <warning descr="[UNUSED_VARIABLE] Variable 'b' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">b</symbolName></warning> = object {
        fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() {
            <symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName descr="Wrapped into a reference object to be modified when captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">x</symbolName></symbolName> = 2
        }
    }

    var <warning descr="[ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE] Variable 'y' is assigned but never accessed" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE">y</symbolName></symbolName></warning> = 1
    fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() {
        <symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName descr="Wrapped into a reference object to be modified when captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y</symbolName></symbolName> = 1
    }
}

fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">refsPlusAssign</symbolName>() {
    var <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE">a</symbolName></symbolName> = 1
    val <warning descr="[UNUSED_VARIABLE] Variable 'v' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">v</symbolName></warning> = {
      <symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName descr="Wrapped into a reference object to be modified when captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">a</symbolName></symbolName> += 2
    }

    var <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName> = 1
    val <warning descr="[UNUSED_VARIABLE] Variable 'b' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">b</symbolName></warning> = object {
        fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() {
            <symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName descr="Wrapped into a reference object to be modified when captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">x</symbolName></symbolName> += 2
        }
    }

    var <symbolName descr="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE"><symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE">y</symbolName></symbolName> = 1
    fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>() {
        <symbolName descr="null" textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName descr="Wrapped into a reference object to be modified when captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y</symbolName></symbolName> += 1
    }
}
