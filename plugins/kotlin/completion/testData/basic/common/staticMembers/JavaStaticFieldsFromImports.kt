import java.lang.Long.MIN_VALUE

fun foo() {
    MAX<caret>
}

// INVOCATION_COUNT: 1
// EXIST_JAVA_ONLY: {"lookupString":"MAX_VALUE","tailText":" (java.lang)","typeText":"Long","attributes":"","allLookupStrings":"MAX_VALUE, getMAX_VALUE","itemText":"Long.MAX_VALUE"}
// ABSENT: { itemText: "Integer.MAX_VALUE" }
