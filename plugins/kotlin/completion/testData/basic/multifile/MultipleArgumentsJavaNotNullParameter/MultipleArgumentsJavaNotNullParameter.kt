fun test(account: JavaNullableAccount, email: String?, password: String?, flags: Int?) {
    account.update(<caret>)
}

// ABSENT:  { "itemText": "email, password, flags" }
