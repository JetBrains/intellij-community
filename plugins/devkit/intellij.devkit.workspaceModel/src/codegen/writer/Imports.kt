package com.intellij.devkit.workspaceModel.codegen.writer

private const val fqnEscape = "#uC03o#"

/**
 * See https://kotlinlang.org/docs/packages.html#default-imports
 */
private val packagesImportedByDefault = setOf(
  "java.lang",
  "kotlin",
  "kotlin.annotation",
  "kotlin.collections",
  "kotlin.comparisons",
  "kotlin.io",
  "kotlin.ranges",
  "kotlin.sequences",
  "kotlin.text",
  "kotlin.jvm",
)

class Imports(private val scopeFqn: String?) {
  val set = mutableSetOf<String>()

  fun findAndRemoveFqns(str: String): String {
    val res = StringBuilder()
    var p = 0
    while (true) {
      var s = str.indexOf(fqnEscape, p)
      if (s == -1) break
      res.append(str, p, s)
      s += fqnEscape.length
      val e = str.indexOf('#', s)
      check(e != -1)

      val fqn = str.substring(s, e)
      val (packageName, name) = fqn.split("@@")
      add(packageName, name)

      p = e + 1
    }
    res.append(str, p, str.length)
    return res.toString()
  }

  fun add(import: String) {
    set.add(import)
  }

  private fun add(packageName: String, name: String) {
    if (packageName != scopeFqn && packageName !in packagesImportedByDefault) {
      set.add("$packageName.$name")
    }
  }
}
