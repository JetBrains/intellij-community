fun some(f: Function1<Int, Int>) {
    f.<caret>
}


// WITH_ORDER
// EXIST: { lookupString: "()", itemText: "()", tailText: "(p1: Int)", typeText: "Int", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: invoke