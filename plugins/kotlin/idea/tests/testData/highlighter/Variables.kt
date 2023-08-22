// EXPECTED_DUPLICATED_HIGHLIGHTING

var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY">x</symbolName></symbolName> = 5

val <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>.<symbolName textAttributesKey="KOTLIN_EXTENSION_PROPERTY">sq</symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>
<symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() {
  return this * this
}

val <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">y</symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> = 1
<symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() {
  return 5.<symbolName textAttributesKey="KOTLIN_EXTENSION_PROPERTY">sq</symbolName> + <symbolName textAttributesKey="KOTLIN_BACKING_FIELD_VARIABLE">field</symbolName> + <symbolName textAttributesKey="KOTLIN_PACKAGE_PROPERTY"><symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE">x</symbolName></symbolName>
}

class <symbolName textAttributesKey="KOTLIN_CLASS">Foo</symbolName>(
    val <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">a</symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>,
    <symbolName textAttributesKey="KOTLIN_PARAMETER">b</symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">String</symbolName>,
    var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">c</symbolName></symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">String</symbolName>
) {
  <symbolName textAttributesKey="KOTLIN_KEYWORD">init</symbolName> {
    <symbolName textAttributesKey="KOTLIN_PARAMETER">b</symbolName>
  }

  fun <symbolName textAttributesKey="KOTLIN_FUNCTION_DECLARATION">f</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">p</symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName> = <symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY">a</symbolName>) {}

  var <symbolName textAttributesKey="KOTLIN_MUTABLE_VARIABLE"><symbolName textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">v</symbolName></symbolName> : <symbolName textAttributesKey="KOTLIN_CLASS">Int</symbolName>
  <symbolName textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() {
    return 1;
  }
  <symbolName textAttributesKey="KOTLIN_KEYWORD">set</symbolName>(<symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName>) {
    <symbolName textAttributesKey="KOTLIN_PARAMETER">value</symbolName>
  }
}

// NO_CHECK_WARNINGS