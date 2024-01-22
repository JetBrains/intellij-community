fun temp() {
    EnhancementFlexibleTypes.test<caret>
}

// EXIST: { lookupString: "testNotNull", tailText:"(string: String)", icon: "Method"}
// EXIST: { lookupString: "testNullable", tailText:"(string: String?)", icon: "Method"}