// KTIJ-24099
// ALLOW_ERRORS

<!HIGHLIGHTING("textAttributesKey='KOTLIN_BUILTIN_ANNOTATION'")!>public<!> class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>Hierarchy<!> <!HIGHLIGHTING("severity='ERROR'; descr='Expecting a top level declaration'")!>extends<!> <!HIGHLIGHTING("severity='ERROR'; descr='Expecting a top level declaration'")!>KotlinOnlyClass<!> <!HIGHLIGHTING("severity='ERROR'; descr='[FUNCTION_DECLARATION_WITH_NO_NAME] Function declaration must have a name'")!><!HIGHLIGHTING("severity='ERROR'; descr='Expecting a top level declaration'")!>{<!>
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: void'")!>void<!> companionMembers(<!HIGHLIGHTING("severity='ERROR'; descr='Expecting an expression'")!>)<!> {
        <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Custom'")!>Custom<!> companionlateinitVariable <!HIGHLIGHTING("severity='ERROR'; descr='Expecting an element'")!>=<!> KotlinOnlyClass<!HIGHLIGHTING("severity='ERROR'; descr='Expecting an element'")!>.<!>companionlateinitVariable<!HIGHLIGHTING("severity='ERROR'; descr='Expecting an element'")!>;<!>
        <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Custom'")!>Custom<!> companionlateinitStaticVariable <!HIGHLIGHTING("severity='ERROR'; descr='Expecting an element'")!>=<!> KotlinOnlyClass<!HIGHLIGHTING("severity='ERROR'; descr='Expecting an element'")!>.<!>companionlateinitStaticVariable<!HIGHLIGHTING("severity='ERROR'; descr='Expecting an element'")!>;<!>

    }<!><!HIGHLIGHTING("severity='ERROR'; descr='Expecting '}''")!><!>
