fun f(){
    JavaClass<String>(<caret>)
}

// EXIST: { itemText: "object : Comparator<String?>{...}" }

// IGNORE_K2
