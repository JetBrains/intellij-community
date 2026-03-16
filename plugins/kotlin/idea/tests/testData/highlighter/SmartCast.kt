// IGNORE_K2
class <symbolName textAttributesKey="KOTLIN_CLASS">My</symbolName>(val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">x</symbolName>: <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>?)

fun <symbolName textAttributesKey="KOTLIN_CLASS">My</symbolName>?.<symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(): <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> {
    if (this == null) return 42
    if (<symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY"><symbolName textAttributesKey="KOTLIN_SMART_CAST_RECEIVER">x</symbolName></symbolName> == null) {
        if (<warning textAttributesKey="WARNING_ATTRIBUTES"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY"><symbolName textAttributesKey="KOTLIN_SMART_CONSTANT"><symbolName textAttributesKey="KOTLIN_SMART_CAST_RECEIVER">x</symbolName></symbolName></symbolName> != null</warning>) {
            <warning textAttributesKey="WARNING_ATTRIBUTES">return</warning> <info descr="Smart cast to kotlin.Nothing" textAttributesKey="KOTLIN_SMART_CAST_VALUE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY"><symbolName textAttributesKey="KOTLIN_SMART_CAST_RECEIVER">x</symbolName></symbolName></info>
        }
        return 13
    }
    return <info descr="Smart cast to kotlin.Int" textAttributesKey="KOTLIN_SMART_CAST_VALUE"><symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY"><symbolName descr="Extension implicit receiver smart cast to My" textAttributesKey="KOTLIN_SMART_CAST_RECEIVER">x</symbolName></symbolName></info>
}
