fun foo() {
    MAX<caret>
}

// INVOCATION_COUNT: 2
// EXIST_JAVA_ONLY: {"lookupString":"MAX_VALUE","tailText":" (java.lang)","typeText":"Int","attributes":"","allLookupStrings":"MAX_VALUE, getMAX_VALUE","itemText":"Integer.MAX_VALUE"}
// EXIST_JAVA_ONLY: {"lookupString":"MAX_VALUE","tailText":" (java.lang)","typeText":"Long","attributes":"","allLookupStrings":"MAX_VALUE, getMAX_VALUE","itemText":"Long.MAX_VALUE"}
// EXIST_JAVA_ONLY: {"lookupString":"MAX_VALUE","tailText":" (java.lang)","typeText":"Short","attributes":"","allLookupStrings":"MAX_VALUE, getMAX_VALUE","itemText":"Short.MAX_VALUE"}
