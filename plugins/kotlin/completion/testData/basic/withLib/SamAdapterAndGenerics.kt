import lib.*;

fun foo(j: JavaInterface<String>) {
    j.<caret>
}

// IGNORE_K2
// EXIST: { lookupString: "execute", itemText: "execute", tailText: "(Task<String!>!, K!)", typeText: "String!", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { lookupString: "execute", itemText: "execute", tailText: "(((String!) -> Unit)!, K!)", typeText: "String!", attributes: "bold", icon: "nodes/abstractMethod.svg"}
