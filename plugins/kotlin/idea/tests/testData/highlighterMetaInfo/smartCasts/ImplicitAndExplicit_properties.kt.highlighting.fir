interface <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Bar<!>

interface <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Foo<!> {
    val <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Bar<!>.<!HIGHLIGHTING("textAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>regularVal<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Bar<!>

    val <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Bar<!>?.<!HIGHLIGHTING("textAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>nullableVal<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Bar<!>
}

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Any<!>.<!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>parameter<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Any<!>?) {

    if (this is <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Foo<!> && <!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>parameter<!> is <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>Bar<!>?) {

        <!HIGHLIGHTING("descr='Smart cast to Bar?'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>parameter<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("descr='Implicit receiver smart cast to Foo'; textAttributesKey='KOTLIN_SMART_CAST_RECEIVER'"), HIGHLIGHTING("textAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>regularVal<!>

        <!HIGHLIGHTING("descr='Smart cast to Bar?'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>parameter<!>.<!HIGHLIGHTING("descr='Implicit receiver smart cast to Foo'; textAttributesKey='KOTLIN_SMART_CAST_RECEIVER'"), HIGHLIGHTING("textAttributesKey='KOTLIN_EXTENSION_PROPERTY'")!>nullableVal<!>

    }
}
