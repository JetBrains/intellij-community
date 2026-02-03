// FIR_IDENTICAL
<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">inline</symbolName> fun <<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">run</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">f</symbolName>: () -> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>) = <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">f</symbolName></symbolName>()
fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">run2</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">f</symbolName>: () -> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>) = <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">f</symbolName></symbolName>()

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">inline</symbolName>() {
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> { <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x</symbolName> }

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x1</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> ({ <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x1</symbolName> })

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x2</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> (<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_NAMED_ARGUMENT">f =</symbolName> { <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x2</symbolName> })

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x3</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
            <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">x3</symbolName>
        }
    }
}

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">notInline</symbolName>() {
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">y2</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> { <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y2</symbolName> }
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run2</symbolName> { <warning descr="[UNUSED_EXPRESSION] The expression is unused" tooltip="[UNUSED_EXPRESSION] The expression is unused" textAttributesKey="WARNING_ATTRIBUTES"><symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y2</symbolName></warning> }

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">y3</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run2</symbolName> { <warning descr="[UNUSED_EXPRESSION] The expression is unused" tooltip="[UNUSED_EXPRESSION] The expression is unused" textAttributesKey="WARNING_ATTRIBUTES"><symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y3</symbolName></warning> }
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> { <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y3</symbolName> }

    // wrapped, using in not inline
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">z</symbolName> = 2
    { <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">z</symbolName> }()

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">z1</symbolName> = 3
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run2</symbolName> { <warning descr="[UNUSED_EXPRESSION] The expression is unused" tooltip="[UNUSED_EXPRESSION] The expression is unused" textAttributesKey="WARNING_ATTRIBUTES"><symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">z1</symbolName></warning> }
}

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">nestedDifferent</symbolName>() { // inline within non-inline and vice-versa
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">y</symbolName> = 1
    {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
            <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y</symbolName>
        }
    }()

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">y1</symbolName> = 1
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
        { <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y1</symbolName> }()
    }
}

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">localFunctionAndClass</symbolName>() {
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">u</symbolName> = 1
    fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">localFun</symbolName>() {
        <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
            <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">u</symbolName>
        }
    }

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">v</symbolName> = 1
    class <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">LocalClass</symbolName> {
        fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">f</symbolName>() {
            <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
                <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">v</symbolName>
            }
        }
    }
}

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">objectExpression</symbolName>() {
    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">u1</symbolName> = 1
    object : <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CONSTRUCTOR">Any</symbolName>() {
        fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">f</symbolName>() {
            <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
                <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">u1</symbolName>
            }
        }
    }

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">u2</symbolName> = 1
    object : <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CONSTRUCTOR">Any</symbolName>() {
        val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">prop</symbolName> = <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
            <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">u2</symbolName>
        }
    }

    val <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_LOCAL_VARIABLE">u3</symbolName> = ""
    object : <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CONSTRUCTOR">Throwable</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> { <symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">u3</symbolName> }) {
    }
}

<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">inline</symbolName> fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">withNoInlineParam</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">noinline</symbolName> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">task1</symbolName>: () -> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>, <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">task2</symbolName>: () -> <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_OBJECT">Unit</symbolName>) {
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">task1</symbolName></symbolName>()
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">task2</symbolName></symbolName>()
}

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">usage</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">param1</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">param2</symbolName>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>) {
    <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">withNoInlineParam</symbolName>({ <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">println</symbolName>(<symbolName descr="Value captured in a closure" tooltip="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">param1</symbolName>) }, { <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">println</symbolName>(<symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">param2</symbolName>) })
}

fun <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">println</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used" tooltip="[UNUSED_PARAMETER] Parameter 'a' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName></warning>: <symbolName descr="null" tooltip="null" textAttributesKey="KOTLIN_CLASS">Any</symbolName>) {}
