fun foo() {
    javax.swing.SwingUtilities.invoke<caret>
}

// IGNORE_K2
// EXIST: { lookupString: "invokeLater", itemText: "invokeLater", tailText: "(doRun: Runnable!)", typeText: "Unit", icon: "fileTypes/java.svg"}
// EXIST: { lookupString: "invokeLater", itemText: "invokeLater", tailText: " {...} (doRun: (() -> Unit)!)", typeText: "Unit", icon: "fileTypes/java.svg"}
