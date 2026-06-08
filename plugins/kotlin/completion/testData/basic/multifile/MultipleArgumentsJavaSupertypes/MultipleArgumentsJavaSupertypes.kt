fun test(email: String, password: StringBuilder, flags: Int, retryCount: Int) {
    JavaAccount.updateBroad(<caret>)
}

// EXIST:  { "itemText": "email, password, flags" }
