fun temp() {
    EnhancementFlexibleTypes.test<caret>
}

// EXIST: { lookupString: "testNotNull", tailText:"(string: String)", icon: "fileTypes/java.svg"}
// EXIST: { lookupString: "testNullable", tailText:"(string: String?)", icon: "fileTypes/java.svg"}