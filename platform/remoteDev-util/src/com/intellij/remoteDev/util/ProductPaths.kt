package com.intellij.remoteDev.util

/**
 * Returns the prefix for [com.intellij.openapi.application.PathManager.getPathsSelector] used by a product with the specified [productCode].
 * 
 * The major part of the version number (e.g., 2024.1) will be appended to the prefix to obtain default names of the directories to
 * store settings, caches, custom plugins, and logs.
 */
fun getPathSelectorPrefixByProductCode(productCode: String): String? {
  return PRODUCT_CODES_TO_PREFIXES[productCode]
}

private val PRODUCT_CODES_TO_PREFIXES = mapOf(
  "IU" to "IntelliJIdea",
  "IC" to "IdeaIC",
  "IE" to "IdeaIE",
  "RM" to "RubyMine",
  "PY" to "PyCharm",
  "PC" to "PyCharmCE",
  "DS" to "DataSpell",
  "PE" to "PyCharmEdu",
  "PS" to "PhpStorm",
  "WS" to "WebStorm",
  "OC" to "AppCode",
  "CL" to "CLion",
  "DB" to "DataGrip",
  "RD" to "Rider",
  "GO" to "GoLand",
  "AI" to "AndroidStudio",
  "CWMG" to "CodeWithMeGuest",
  "JBC" to "JetBrainsClient",
  "RR" to "RustRover"
)
