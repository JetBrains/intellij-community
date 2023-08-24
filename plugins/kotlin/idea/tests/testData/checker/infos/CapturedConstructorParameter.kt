// FIR_IDENTICAL
<symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">import</symbolName> kotlin.reflect.<symbolName descr="null" textAttributesKey="KOTLIN_TRAIT">KProperty</symbolName>

interface <symbolName descr="null" textAttributesKey="KOTLIN_TRAIT">T</symbolName>
class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">T1</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 't' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">t</symbolName></warning>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>): <symbolName descr="null" textAttributesKey="KOTLIN_TRAIT">T</symbolName>

<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">inline</symbolName> fun <<symbolName descr="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>> <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">run</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">f</symbolName>: () -> <symbolName descr="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>) = <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER"><symbolName descr="null" textAttributesKey="KOTLIN_VARIABLE_AS_FUNCTION">f</symbolName></symbolName>()


class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Delegate</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'd' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">d</symbolName></warning>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>) {
    <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">operator</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">getValue</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">k</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Any</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">m</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_TRAIT">KProperty</symbolName><*>) {}
}

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">A</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">y</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">t</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">d</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>): <symbolName descr="null" textAttributesKey="KOTLIN_TRAIT">T</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">by</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_CONSTRUCTOR">T1</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">t</symbolName>) {
    val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">a</symbolName> = <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">y</symbolName>
    val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">b</symbolName></symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">by</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_CONSTRUCTOR">Delegate</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">d</symbolName>)
}

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">A2</symbolName><<symbolName descr="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">x</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">y</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">t</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName>) {
    val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">t1</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_TYPE_PARAMETER">T</symbolName> = <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">t</symbolName>

    val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">x1</symbolName> = <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> { <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">x</symbolName> }
    <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">init</symbolName> {
        <symbolName descr="null" textAttributesKey="KOTLIN_PACKAGE_FUNCTION_CALL">run</symbolName> {
            <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">y</symbolName>
        }
    }
}


//captured

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">B</symbolName>(
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">x</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>,
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">y</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>,
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">t</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>,
        <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">d</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>
) {
    <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">init</symbolName> {
        class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">C</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">a</symbolName></warning>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> = <symbolName descr="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">x</symbolName>): <symbolName descr="null" textAttributesKey="KOTLIN_TRAIT">T</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">by</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_CONSTRUCTOR">T1</symbolName>(<symbolName descr="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">t</symbolName>) {
            val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">a</symbolName> = <symbolName descr="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y</symbolName>
            val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">b</symbolName></symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">by</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_CONSTRUCTOR">Delegate</symbolName>(<symbolName descr="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">d</symbolName>)
        }
    }
}

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">B2</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">x</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">y</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>) {

    val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">x1</symbolName> =  { <symbolName descr="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">x</symbolName> }()
    <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">init</symbolName> {
        {
            <symbolName descr="Value captured in a closure" textAttributesKey="KOTLIN_WRAPPED_INTO_REF">y</symbolName>
        }()
    }
}
