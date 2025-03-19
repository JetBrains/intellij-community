fun foo() {
    try {

    }
    <caret>
}

// IGNORE_K2
// EXIST: catch
// EXIST: finally
// EXIST: false
// EXIST: null
// EXIST: true
// NOTHING_ELSE
