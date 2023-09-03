package first

class A : NotImported<caret>

// IGNORE_K2
// AUTOCOMPLETE_SETTING: true
// EXIST: NotImportedClass
// NOTHING_ELSE
