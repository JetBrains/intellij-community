fun temp() {
    EnhancementFlexibleTypes.test<caret>
}

// IGNORE_K2
// EXIST: { lookupString: "testNotNull", tailText:"(string: String)", icon: "fileTypes/java.svg"}
// EXIST: { lookupString: "testNullable", tailText:"(string: String?)", icon: "fileTypes/java.svg"}