// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Base<!>

interface <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Foo<!> {
    <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>operator<!> fun <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Base<!>.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>invoke<!>() {}
}

fun <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Foo<!>.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>(<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>param<!>: <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_CLASS'")!>Any<!>) {
    if (<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>param<!> is <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_TRAIT'")!>Base<!>) {

        <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Smart cast to Base'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>param<!>.<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_EXTENSION_FUNCTION_CALL'")!>invoke<!>() // call 1

        <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION_LIKE'")!>(<!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Smart cast to Base'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'")!>param<!>)<!>() // call 2

        <!HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; descr='Smart cast to Base'; highlightingTextAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_PARAMETER'"), HIGHLIGHTING("severity='SYMBOL_TYPE_SEVERITY'; highlightingTextAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION_LIKE'")!>param<!>() // call 3
    }
}
