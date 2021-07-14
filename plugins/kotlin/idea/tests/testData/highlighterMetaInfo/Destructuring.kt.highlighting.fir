<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>data<!> class <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Box<!>(val <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_INSTANCE_PROPERTY'")!>v<!>: <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!>)
fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>consume<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>x<!>: <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!>) {}

fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>some<!>() {
    val (s) = <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Box<!>(0)
    var (x) = <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Box<!>(1)

    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>consume<!>(s)
    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>consume<!>(x)

    x = x * 2 + 2
    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>consume<!>(x)
}
