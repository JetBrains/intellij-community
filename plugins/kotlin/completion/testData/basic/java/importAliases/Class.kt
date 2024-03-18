import java.io.FileInputStream as FileInputStreamMy

fun foo(): FileInputS<caret>

// IGNORE_K2
// WITH_ORDER
// EXIST: { lookupString: "FileInputStreamMy", itemText: "FileInputStreamMy", tailText: " (java.io.FileInputStream)", icon: "RowIcon(icons=[Class, null])"}
// EXIST: { lookupString: "FileInputStream", itemText: "FileInputStream", tailText: " (java.io)", icon: "RowIcon(icons=[Class, null])"}
