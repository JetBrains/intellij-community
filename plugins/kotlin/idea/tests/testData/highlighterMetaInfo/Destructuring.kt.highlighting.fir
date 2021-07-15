<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>data<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Box<!>(val <!HIGHLIGHTING("textAttributesKey='KOTLIN_INSTANCE_PROPERTY'")!>v<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!>)
fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>consume<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>x<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!>) {}

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>some<!>() {
    val (s) = <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Box<!>(0)
    var (x) = <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Box<!>(1)

    <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>consume<!>(s)
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>consume<!>(x)

    x = x * 2 + 2
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>consume<!>(x)
}
