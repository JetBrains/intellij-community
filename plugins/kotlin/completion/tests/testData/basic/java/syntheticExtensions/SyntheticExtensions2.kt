fun Thread.foo(urlConnection: java.net.URLConnection) {
    with (urlConnection) {
        <caret>
    }
}

// EXIST: { lookupString: "priority", itemText: "priority", tailText: " (from getPriority()/setPriority())", typeText: "Int", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}
// EXIST: { lookupString: "isDaemon", itemText: "isDaemon", tailText: " (from isDaemon()/setDaemon())", typeText: "Boolean", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}
// EXIST: { lookupString: "url", itemText: "url", tailText: " (from getURL())", typeText: "URL!", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// ABSENT: getPriority
// ABSENT: setPriority
// ABSENT: { itemText: "isDaemon", tailText: "()" }
// ABSENT: setDaemon
// ABSENT: getURL
