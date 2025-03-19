// EXPECTED_DUPLICATED_HIGHLIGHTING

val <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY">packageSize</symbolName> = 0
val <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">packageSizeGetter</symbolName></symbolName>
<symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() = <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY">packageSize</symbolName> * 2

var <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">packageSizeSetter</symbolName></symbolName></symbolName> = 5
<symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">field</symbolName></symbolName> = <symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName> * 2
}

var <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">packageSizeBean</symbolName></symbolName></symbolName> = 5
<symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() = <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY">packageSize</symbolName> * 2
<symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE">field</symbolName></symbolName> = <symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName> * 2
}


class <symbolName textAttributesKey="KOTLIN_CLASS">test</symbolName>() {
    // no highlighting check
    val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">size</symbolName> = 0

    val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">classSize</symbolName> = 0

    val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">classSizeGetter</symbolName></symbolName>
    <symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() = <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">classSize</symbolName> * 2

    var <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">classSizeSetter</symbolName></symbolName></symbolName> = 5
    <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName>) {
        <symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">field</symbolName></symbolName> = <symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName> * 2
    }

    var <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">classSizeBean</symbolName></symbolName></symbolName> = 5
    <symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() = <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">classSize</symbolName> * 2
    <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName>) {
        <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE">field</symbolName></symbolName> = <symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName> * 2
    }

    fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">callCustomPD</symbolName>() {
        <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">classSizeBean</symbolName></symbolName></symbolName> = 30
    }
}
