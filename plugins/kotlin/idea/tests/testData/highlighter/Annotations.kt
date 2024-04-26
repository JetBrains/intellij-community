// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING

<symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Target</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_ENUM">AnnotationTarget</symbolName>.<symbolName descr="null" textAttributesKey="KOTLIN_ENUM_ENTRY">CLASS</symbolName>, <symbolName descr="null" textAttributesKey="KOTLIN_ENUM">AnnotationTarget</symbolName>.<symbolName descr="null" textAttributesKey="KOTLIN_ENUM_ENTRY">EXPRESSION</symbolName>)
<symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Retention</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_ENUM">AnnotationRetention</symbolName>.<symbolName descr="null" textAttributesKey="KOTLIN_ENUM_ENTRY">SOURCE</symbolName>)
<symbolName descr="null">annotation</symbolName> class <symbolName descr="null">Ann</symbolName>

<symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Ann</symbolName> class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">A</symbolName>

fun <symbolName descr="null">bar</symbolName>(<symbolName descr="null">block</symbolName>: () -> <symbolName descr="null">Int</symbolName>) = <symbolName descr="null"><symbolName descr="null">block</symbolName></symbolName>()

<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">private</symbolName>
fun <symbolName descr="null">foo</symbolName>() {
    1 + <symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Ann</symbolName> 2

    <warning descr="[ANNOTATIONS_ON_BLOCK_LEVEL_EXPRESSION_ON_THE_SAME_LINE] Annotations on block-level expressions are being parsed differently depending on presence of a new line after them. Use new line if whole block-level expression must be annotated or wrap annotated expression in parentheses" textAttributesKey="WARNING_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Ann</symbolName> 3</warning> + 4

    <symbolName descr="null"><symbolName descr="null">bar</symbolName></symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@Ann</symbolName> { 1 }

    <symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: Err" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">Err</error></symbolName>
    <warning descr="[UNUSED_EXPRESSION] The expression is unused" textAttributesKey="WARNING_ATTRIBUTES">5</warning>
}

<symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: Err" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">Err</error></symbolName> class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS"><symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Err1</symbolName></symbolName>

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">NotAnn</symbolName>
<error descr="[NOT_AN_ANNOTATION_CLASS] 'NotAnn' is not an annotation class" textAttributesKey="ERRORS_ATTRIBUTES"><symbolName descr="null" textAttributesKey="KOTLIN_ANNOTATION">@<symbolName descr="null" textAttributesKey="KOTLIN_CLASS">NotAnn</symbolName></symbolName></error>
class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">C</symbolName>
