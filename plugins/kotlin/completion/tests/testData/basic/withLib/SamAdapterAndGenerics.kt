import lib.*;

fun foo(j: JavaInterface<String>) {
    j.<caret>
}

// EXIST: { lookupString: "execute", itemText: "execute", tailText: "(Task<String!>!, K!)", typeText: "String!", attributes: "bold", icon: "fileTypes/javaClass.svg"}
// EXIST: { lookupString: "execute", itemText: "execute", tailText: "(((String!) -> Unit)!, K!)", typeText: "String!", attributes: "bold", icon: "fileTypes/javaClass.svg"}
