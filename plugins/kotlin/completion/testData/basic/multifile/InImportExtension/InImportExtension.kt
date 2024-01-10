package first

import second.<caret>

// EXIST: { itemText: "extensionFun", tailText: "() for String in second", attributes: "" }
// EXIST: { itemText: "extensionVal", tailText: " for Int in second", attributes: "" }
// NOTHING_ELSE