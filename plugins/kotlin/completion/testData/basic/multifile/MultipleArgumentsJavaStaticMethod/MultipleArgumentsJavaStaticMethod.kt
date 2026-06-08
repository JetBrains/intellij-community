fun test(email: String, password: String, flags: Int, backupEmail: String) {
    JavaAccount.updateStatic(<caret>)
}

// EXIST:  { "itemText": "email, password, flags" }
