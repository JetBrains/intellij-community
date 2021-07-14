<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>@Target<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ENUM'")!>AnnotationTarget<!>.CLASS, <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ENUM'")!>AnnotationTarget<!>.EXPRESSION)
<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>@Retention<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ENUM'")!>AnnotationRetention<!>.SOURCE)
<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>annotation<!> class <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>Ann<!>

<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>@Ann<!> class <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>A<!>

fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>bar<!>(<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'")!>block<!>: () -> <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_CLASS'")!>Int<!>) = <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PARAMETER'"), HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION'")!>block<!>()

<!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>private<!>
fun <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>foo<!>() {
    1 + <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>@Ann<!> 2

    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>@Ann<!> 3 + 4

    <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_PACKAGE_FUNCTION_CALL'")!>bar<!> <!HIGHLIGHTING("severity='INFORMATION'; textAttributesKey='KOTLIN_ANNOTATION'")!>@Ann<!> { 1 }
}
