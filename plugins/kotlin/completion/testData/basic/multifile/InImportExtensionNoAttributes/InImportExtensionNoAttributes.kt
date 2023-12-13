package first

import second.<caret>

// EXIST: { itemText: "extensionFun", tailText: "() for String in second" }
// EXIST: { itemText: "extensionVal", tailText: " for Int in second" }
// NOTHING_ELSE