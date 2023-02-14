// EXPECTED_DUPLICATED_HIGHLIGHTING

<info textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">open</info> class <info textAttributesKey="KOTLIN_CLASS">C</info> {
    var <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">foo</info></info>: <info textAttributesKey="KOTLIN_CLASS">Int</info> = 0
    <info textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">private</info> <info textAttributesKey="KOTLIN_KEYWORD">set</info>
    var <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">bar</info></info>: <info textAttributesKey="KOTLIN_CLASS">Int</info> = 0
    <info textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">protected</info> <info textAttributesKey="KOTLIN_KEYWORD">set</info>
    var <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">baz</info></info>: <info textAttributesKey="KOTLIN_CLASS">Int</info> = 0
    <info textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">internal</info> <info textAttributesKey="KOTLIN_KEYWORD">set</info>
    var <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">qux</info></info>: <info textAttributesKey="KOTLIN_CLASS">Int</info> = 0
    <info textAttributesKey="KOTLIN_KEYWORD">set</info>
}

fun <info textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</info>(<info textAttributesKey="KOTLIN_PARAMETER">c</info>: <info textAttributesKey="KOTLIN_CLASS">C</info>) {
    <info textAttributesKey="KOTLIN_PARAMETER">c</info>.<info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">foo</info>
    <info textAttributesKey="KOTLIN_PARAMETER">c</info>.<info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">bar</info>
    <info textAttributesKey="KOTLIN_PARAMETER">c</info>.<info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">baz</info></info>
    <info textAttributesKey="KOTLIN_PARAMETER">c</info>.<info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">qux</info></info>
}

class <info textAttributesKey="KOTLIN_CLASS">Test</info> : <info textAttributesKey="KOTLIN_CONSTRUCTOR">C</info>() {
    fun <info textAttributesKey="KOTLIN_FUNCTION_DECLARATION">test</info>() {
        <info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">foo</info>
        <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">bar</info></info>
        <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">baz</info></info>
        <info textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><info textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">qux</info></info>
    }
}
