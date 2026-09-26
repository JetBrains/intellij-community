fun test(email: String, password: String, flags: Int, backupEmail: String) {
    JavaAccount(<caret>)
}

// EXIST:  { "itemText": "email, password, flags" }
// EXIST:  { "itemText": "email, password" }
