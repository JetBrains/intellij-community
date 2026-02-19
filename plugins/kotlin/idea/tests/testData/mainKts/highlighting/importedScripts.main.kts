// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

// FILE: mainFile.main.kts
@file:Import("imported.main.kts")

getImportedKtsText()
importedKts
ImportedKts()

getImportedMainKtsText()
importedMainKts
ImportedMainKts()

// FILE: imported.main.kts
@file:Import("imported.kts")

fun getImportedMainKtsText() = "fromImportedKts"
val importedMainKts = "importedMainKts"
class ImportedMainKts()

ImportedKts()
getImportedKtsText()
importedKts

// FILE: imported.kts
fun getImportedKtsText() = "fromImportedKts"
val importedKts = "fromImportedKts"

class ImportedKts()
