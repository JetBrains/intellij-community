// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING
<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">open</symbolName> class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Foo</symbolName> {
    <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">constructor</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'i' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">i</symbolName></warning>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>)
}

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Bar</symbolName> : <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Foo</symbolName> {
    <error descr="[EXPLICIT_DELEGATION_CALL_REQUIRED] Explicit 'this' or 'super' call is required. There is no constructor in superclass that can be called without arguments" textAttributesKey="ERRORS_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">constructor</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">s</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>)</error>
}

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">F</symbolName>(<warning descr="[UNUSED_PARAMETER] Parameter 'foo' is never used" textAttributesKey="NOT_USED_ELEMENT_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">foo</symbolName></warning>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>) {
    <error descr="[PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED] Primary constructor call expected" textAttributesKey="ERRORS_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">constructor</symbolName>()</error> {}
}

<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">enum</symbolName> class <symbolName descr="null" textAttributesKey="KOTLIN_ENUM">E</symbolName>(val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY">a</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">String</symbolName>) {
    <symbolName descr="null" textAttributesKey="KOTLIN_ENUM_ENTRY">A</symbolName>;
    <error descr="[PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED] Primary constructor call expected" textAttributesKey="ERRORS_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">constructor</symbolName>()</error>
}
