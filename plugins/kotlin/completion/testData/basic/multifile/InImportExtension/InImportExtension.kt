package first

import second.<caret>

// when test passes in K2, InImportExtensionNoAttributes may be deleted - it duplicates this one without checking attributes
// IGNORE_K2
// EXIST: { itemText: "extensionFun", tailText: "() for String in second", attributes: "" }
// EXIST: { itemText: "extensionVal", tailText: " for Int in second", attributes: "" }
// NOTHING_ELSE