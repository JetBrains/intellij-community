<symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">abstract</symbolName> class <symbolName descr="null" textAttributesKey="KOTLIN_ABSTRACT_CLASS">Base</symbolName> {
  <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">abstract</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(<TYPO descr="Typo: In word 'oher'" textAttributesKey="TYPO"><symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">oher</symbolName></TYPO>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>)

  <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">abstract</symbolName> val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY"><TYPO descr="Typo: In word 'smal'" textAttributesKey="TYPO">smal</TYPO>Val</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>
  <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">abstract</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION"><TYPO descr="Typo: In word 'smal'" textAttributesKey="TYPO">smal</TYPO>Fun</symbolName>()
}

class <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Other</symbolName> : <symbolName descr="null" textAttributesKey="KOTLIN_CONSTRUCTOR">Base</symbolName>() {
  <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">override</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">foo</symbolName>(<symbolName descr="null" textAttributesKey="KOTLIN_PARAMETER">oher</symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName>) {
  }

  <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">override</symbolName> val <symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION"><symbolName descr="null" textAttributesKey="KOTLIN_INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION">smalVal</symbolName></symbolName>: <symbolName descr="null" textAttributesKey="KOTLIN_CLASS">Int</symbolName> <symbolName descr="null" textAttributesKey="KOTLIN_KEYWORD">get</symbolName>() = 1
  <symbolName descr="null" textAttributesKey="KOTLIN_BUILTIN_ANNOTATION">override</symbolName> fun <symbolName descr="null" textAttributesKey="KOTLIN_FUNCTION_DECLARATION">smalFun</symbolName>() {}
}

