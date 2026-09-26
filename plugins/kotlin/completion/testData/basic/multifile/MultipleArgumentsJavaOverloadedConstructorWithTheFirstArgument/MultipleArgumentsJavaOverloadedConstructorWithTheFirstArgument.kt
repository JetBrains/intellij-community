fun test(email: String, password: String, flags: Int) {
    JavaAccount(email, <caret>)
}

// EXIST:  { "itemText": "password, flags" }
