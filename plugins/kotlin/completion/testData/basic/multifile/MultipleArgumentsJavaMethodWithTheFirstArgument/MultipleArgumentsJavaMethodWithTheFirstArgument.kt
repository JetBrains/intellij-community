fun test(account: JavaAccount, email: String, password: String, flags: Int?, backupFlags: Int?) {
    account.update(email, <caret>)
}

// EXIST:  { "itemText": "password, flags" }
