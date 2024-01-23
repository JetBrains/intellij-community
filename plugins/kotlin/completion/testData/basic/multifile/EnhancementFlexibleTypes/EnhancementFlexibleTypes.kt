fun temp() {
    EnhancementFlexibleTypes.test<caret>
}

// IGNORE_K2
// EXIST: { lookupString: "testNotNull", tailText:"(string: String)", icon: "Method"}
// EXIST: { lookupString: "testNullable", tailText:"(string: String?)", icon: "Method"}