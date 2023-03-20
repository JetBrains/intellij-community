package some.long.pkg

interface <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>WithInvoke<!> {
    <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>operator<!> fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>invoke<!>() {}

    <!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>operator<!> fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>plus<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>i<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Int<!>)
}

val <!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Any<!> = <!HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>Any<!>()

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test<!>() {
    if (<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!> is <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>WithInvoke<!>) {

        <!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!>.<!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>invoke<!>()

        some.long.pkg.<!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'"), HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'"), HIGHLIGHTING("textAttributesKey='KOTLIN_VARIABLE_AS_FUNCTION_LIKE'")!>topLevelProp<!>()

        val <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>value<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>WithInvoke<!> = <!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!>

        val <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>value2<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>WithInvoke<!> = (<!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!>)

        val <!HIGHLIGHTING("textAttributesKey='KOTLIN_LOCAL_VARIABLE'")!>value3<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>WithInvoke<!> = ((<!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!>))

        <!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!> + 1

        (<!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!>) + 1

        ((<!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!>some.long.pkg.<!HIGHLIGHTING("textAttributesKey='KOTLIN_PACKAGE_PROPERTY'")!>topLevelProp<!><!>)) + 1
    }
}

<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>data<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>WithProp<!>(val <!HIGHLIGHTING("textAttributesKey='KOTLIN_INSTANCE_PROPERTY'")!>prop<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Any<!>?)

fun <!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_DECLARATION'")!>test2<!>(<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>param<!>: <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>WithProp<!>?) {
    if (<!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>param<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_INSTANCE_PROPERTY'")!>prop<!> is <!HIGHLIGHTING("textAttributesKey='KOTLIN_TRAIT'")!>WithInvoke<!>?) {

        <!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke?'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!><!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>param<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_INSTANCE_PROPERTY'")!>prop<!><!><!HIGHLIGHTING("textAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>invoke<!>()

        <!HIGHLIGHTING("descr='Smart cast to some/long/pkg/WithInvoke?'; textAttributesKey='KOTLIN_SMART_CAST_VALUE'")!><!HIGHLIGHTING("textAttributesKey='KOTLIN_PARAMETER'")!>param<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_INSTANCE_PROPERTY'")!>prop<!><!><!HIGHLIGHTING("textAttributesKey='KOTLIN_SAFE_ACCESS'")!>?.<!><!HIGHLIGHTING("textAttributesKey='KOTLIN_FUNCTION_CALL'")!>plus<!>(10)

    }
}
