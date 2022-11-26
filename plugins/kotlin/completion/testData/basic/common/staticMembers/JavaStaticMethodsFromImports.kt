import java.util.Collections.singletonList

fun foo() {
    singl<caret>
}

// INVOCATION_COUNT: 1
// EXIST_JAVA_ONLY: {"lookupString":"singleton","tailText":"(T!) (java.util)","typeText":"(Mutable)Set<T!>","attributes":"","allLookupStrings":"singleton","itemText":"Collections.singleton"}
// ABSENT: { itemText: "Collections.singletonList" }
// EXIST_JAVA_ONLY: {"lookupString":"singletonList","tailText":"(T!)","typeText":"(Mutable)List<T!>","attributes":"","allLookupStrings":"singletonList","itemText":"singletonList"}
