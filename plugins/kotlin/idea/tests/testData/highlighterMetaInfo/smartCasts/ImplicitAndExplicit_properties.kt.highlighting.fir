// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Bar<!>

interface <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Foo<!> {
    val <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Bar<!>.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>regularVal<!>: <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Bar<!>

    val <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Bar<!>?.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>nullableVal<!>: <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Bar<!>
}

fun <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_CLASS'")!>Any<!>.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>parameter<!>: <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_CLASS'")!>Any<!>?) {

    if (this is <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Foo<!> && <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>parameter<!> is <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Bar<!>?) {

        <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Smart cast to Bar?'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>parameter<!><!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Implicit receiver smart cast to Foo'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_RECEIVER'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>regularVal<!>

        <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Smart cast to Bar?'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>parameter<!>.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Implicit receiver smart cast to Foo'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_RECEIVER'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>nullableVal<!>

    }
}
