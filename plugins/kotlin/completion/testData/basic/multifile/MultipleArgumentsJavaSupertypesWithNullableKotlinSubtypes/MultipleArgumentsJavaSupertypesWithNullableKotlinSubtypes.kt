fun test(email: StringBuilder?, password: StringBuilder?, flags: Int?, backupEmail: StringBuilder?) {
    JavaAccount.updateBroad(<caret>)
}

// EXIST:  { "itemText": "email, password, flags" }
