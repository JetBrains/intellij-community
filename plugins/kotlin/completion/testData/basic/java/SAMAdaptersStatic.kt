// FIR_COMPARISON
fun foo() {
    javax.swing.SwingUtilities.invokeLate<caret>
}

// EXIST: { lookupString: "invokeLater", itemText: "invokeLater", tailText: "(doRun: Runnable!)", typeText: "Unit", icon: "Method"}
// EXIST: { lookupString: "invokeLater", itemText: "invokeLater", tailText: " {...} (doRun: (() -> Unit)!)", typeText: "Unit", icon: "Method"}
// NOTHING_ELSE