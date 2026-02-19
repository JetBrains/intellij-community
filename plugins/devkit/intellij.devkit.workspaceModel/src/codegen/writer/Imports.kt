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
  fun isNotEmpty(): Boolean = set.isNotEmpty() || wildcardImports.isNotEmpty()
  
  val imports: Set<String>
    get() = set + wildcardImports.map { "$it.*" }
  
  private val set = mutableSetOf<String>()
  private val wildcardImports = mutableSetOf<String>()

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
    val importSegments = import.split('.')
    if (importSegments.size < 2) set.add(import)
    else {
      val (packageName, name) = import
        .split('.')
        .let {
          it.dropLast(1).joinToString(".") to it.last()
        }
      add(packageName, name)
    }
  }

  private fun String.packageRegex() = Regex("^${replace(".", "\\.")}\\.[^.]*$")

  private fun add(packageName: String, name: String) {
    if (packageName == scopeFqn || packageName in packagesImportedByDefault || packageName in wildcardImports) {
      return
    } else if (name == "*") {
      val packageRegex = packageName.packageRegex()
      set.removeIf { it.matches(packageRegex) }
      wildcardImports.add(packageName)
    } else {
      set.add("$packageName.$name")
    }
  }
}
