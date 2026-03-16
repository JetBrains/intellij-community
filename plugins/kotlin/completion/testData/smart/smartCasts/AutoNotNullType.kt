fun f(p: String?) {
    if (p != null){
        var a : String = <caret>
    }
}

// EXIST: { itemText:"p" }

// IGNORE_K2
