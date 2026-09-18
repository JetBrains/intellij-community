// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.testFramework

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Verifies plugin manager specification files and their links to source files and tests.
 *
 * [repositoryRoot] defines the base for code backlinks. [scanRoots] limit target matching and backlink checks.
 * [sectionRequirements] defines the required sections for each specification path relative to [specRoot].
 */
class PluginManagerSpecVerifier(
  repositoryRoot: Path,
  specRoot: Path,
  scanRoots: List<Path>,
  sectionRequirements: Map<String, Set<String>>,
) {
  private val repositoryRoot = repositoryRoot.toAbsolutePath().normalize()
  private val specRoot = specRoot.toAbsolutePath().normalize()
  private val scanRoots = scanRoots.map { it.toAbsolutePath().normalize() }
  private val sectionRequirements = sectionRequirements.mapKeys { (path, _) ->
    Path.of(path).normalize().invariantSeparatorsPathString
  }

  fun validate(): List<String> {
    val violations = ArrayList<String>()
    if (!Files.isDirectory(specRoot)) {
      return listOf("${display(specRoot)}: specification directory does not exist")
    }

    val scannedFiles = filesUnder(scanRoots)
    val scannedFileNames = scannedFiles.mapTo(HashSet()) { repositoryRoot.relativize(it).invariantSeparatorsPathString }
    val markdownDocuments = filesUnder(listOf(specRoot))
      .filter { it.name.endsWith(".md") }
      .sorted()
      .map { path ->
        val text = path.readText()
        MarkdownDocument(path, text)
      }
    val specs = markdownDocuments
      .filter { it.path.name.endsWith(".spec.md") }
      .map { document -> SpecDocument(document.path, document.text, parseFrontmatter(document.text)) }
    if (specs.isEmpty()) {
      return listOf("${display(specRoot)}: no specification files were found")
    }

    val specsByRelativePath = specs.associateBy { specRoot.relativize(it.path).invariantSeparatorsPathString }
    for ((relativePath, spec) in specsByRelativePath) {
      val requiredSections = sectionRequirements[relativePath]
      if (requiredSections == null) {
        violations += "${display(spec.path)}: section requirements are not configured"
      }
      validateStructure(spec, requiredSections.orEmpty(), violations)
      validateTargets(spec, scannedFileNames, violations)
      validateTestLinks(spec, violations)
    }
    for (missingSpec in sectionRequirements.keys - specsByRelativePath.keys) {
      violations += "${display(specRoot.resolve(missingSpec))}: configured specification does not exist"
    }
    for (document in markdownDocuments) {
      validateMarkdownLinks(document, violations)
    }
    validateBacklinks(specs, scannedFiles, violations)
    return violations.sorted()
  }

  private fun validateStructure(spec: SpecDocument, requiredSections: Set<String>, violations: MutableList<String>) {
    val frontmatter = spec.frontmatter
    if (frontmatter == null) {
      violations += "${display(spec.path)}: YAML frontmatter is missing or incomplete"
      return
    }
    for (field in REQUIRED_FIELDS) {
      if (frontmatter.values[field].isNullOrBlank()) {
        violations += "${display(spec.path)}: frontmatter field '$field' is missing or empty"
      }
    }
    if (frontmatter.targets.isEmpty()) {
      violations += "${display(spec.path)}: frontmatter declares no targets"
    }

    val name = frontmatter.values["name"]
    val title = spec.text.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
    if (name != null && title != name) {
      violations += "${display(spec.path)}: H1 title '$title' does not match frontmatter name '$name'"
    }

    val status = STATUS.matchEntire(spec.text.lineSequence().firstOrNull { it.startsWith("Status:") }.orEmpty())
    if (status == null || status.groupValues[1].isBlank()) {
      violations += "${display(spec.path)}: a nonempty 'Status:' value is required"
    }
    val dateText = DATE.matchEntire(spec.text.lineSequence().firstOrNull { it.startsWith("Date:") }.orEmpty())?.groupValues?.get(1)
    if (dateText == null || !isIsoDate(dateText)) {
      violations += "${display(spec.path)}: 'Date:' must contain a valid ISO-8601 date"
    }

    val sections = spec.text.lineSequence()
      .filter { it.startsWith("## ") }
      .map { it.removePrefix("## ").trim() }
      .toSet()
    for (section in requiredSections - sections) {
      violations += "${display(spec.path)}: required section '$section' is missing"
    }
  }

  private fun validateTargets(
    spec: SpecDocument,
    scannedFileNames: Set<String>,
    violations: MutableList<String>,
  ) {
    for (target in spec.frontmatter?.targets.orEmpty()) {
      if (!isRelativeReference(spec, target, violations)) continue
      if (!resolvesInScan(spec, target, scannedFileNames)) {
        violations += "${display(spec.path)}: target '$target' matches no file in the configured scan roots"
      }
    }
  }

  private fun validateTestLinks(spec: SpecDocument, violations: MutableList<String>) {
    for (match in TEST_LINK.findAll(spec.text)) {
      val link = match.groupValues[1].trimEnd(')', ',', '.')
      if (!isRelativeReference(spec, link, violations)) continue
      val target = spec.path.parent.resolve(link).normalize()
      if (!Files.isRegularFile(target)) {
        violations += "${display(spec.path)}:${lineOf(spec.text, match.range.first)}: [@test] '$link' does not resolve"
        continue
      }
      for (methodName in testLinkMethodNames(spec.text, match.range.last + 1)) {
        if (!declaresFunction(target.readText(), methodName)) {
          violations += "${display(spec.path)}:${lineOf(spec.text, match.range.first)}: [@test] '$link' does not declare '$methodName'"
        }
      }
    }
  }

  private fun validateMarkdownLinks(document: MarkdownDocument, violations: MutableList<String>) {
    for (match in MARKDOWN_LINK.findAll(document.text)) {
      val link = match.groupValues[1]
      if ("://" in link) continue
      if (!isRelativeReference(document, link, violations)) continue
      if (!Files.exists(document.path.parent.resolve(link).normalize())) {
        violations += "${display(document.path)}:${lineOf(document.text, match.range.first)}: Markdown link '$link' does not resolve"
      }
    }
  }

  private fun validateBacklinks(
    specs: List<SpecDocument>,
    scannedFiles: List<Path>,
    violations: MutableList<String>,
  ) {
    val specsByPath = specs.associateBy { it.path }
    val backlinkCounts = specs.associate { it.path to 0 }.toMutableMap()
    val specPrefix = repositoryRoot.relativize(specRoot).invariantSeparatorsPathString.trimEnd('/') + "/"
    for (source in scannedFiles.filter { it.name.endsWith(".kt") || it.name.endsWith(".java") }) {
      val sourceText = source.readText()
      for (match in BACKLINK.findAll(sourceText)) {
        val link = match.groupValues[1]
        val target = repositoryRoot.resolve(link).normalize()
        if (Path.of(link).isAbsolute || !target.startsWith(repositoryRoot) ||
            !Files.isRegularFile(target) || !target.name.endsWith(".spec.md")) {
          violations += "${display(source)}:${lineOf(sourceText, match.range.first)}: backlink '$link' does not resolve"
          continue
        }
        if (!link.startsWith(specPrefix)) continue
        val spec = specsByPath[target]
        if (spec == null) {
          violations += "${display(source)}:${lineOf(sourceText, match.range.first)}: backlink '$link' does not resolve"
          continue
        }
        backlinkCounts[target] = backlinkCounts.getValue(target) + 1
        val sourceMatchesTarget = spec.frontmatter?.targets.orEmpty().any { referenceMatches(spec, it, source) }
        if (!sourceMatchesTarget) {
          violations += "${display(source)}:${lineOf(sourceText, match.range.first)}: backlink '$link' is outside the specification targets"
        }
      }
    }
    for ((spec, count) in backlinkCounts) {
      if (count == 0) {
        violations += "${display(spec)}: no configured source file has an @spec backlink"
      }
    }
  }

  private fun isRelativeReference(document: MarkdownDocument, reference: String, violations: MutableList<String>): Boolean {
    val resolved = document.path.parent.resolve(reference.toPathSafeGlob()).normalize()
    if (Path.of(reference.toPathSafeGlob()).isAbsolute || !resolved.startsWith(repositoryRoot)) {
      violations += "${display(document.path)}: reference '$reference' leaves the repository root"
      return false
    }
    return true
  }

  private fun resolvesInScan(spec: SpecDocument, reference: String, scannedFileNames: Set<String>): Boolean {
    val resolved = spec.path.parent.resolve(reference.toPathSafeGlob()).normalize()
    val relativePattern = repositoryRoot.relativize(resolved).invariantSeparatorsPathString.restorePathSafeGlob()
    if ('*' !in reference && '?' !in reference) return relativePattern in scannedFileNames
    return scannedFileNames.any(globToRegex(relativePattern)::matches)
  }

  private fun referenceMatches(spec: SpecDocument, reference: String, source: Path): Boolean {
    val resolved = spec.path.parent.resolve(reference.toPathSafeGlob()).normalize()
    if (!resolved.startsWith(repositoryRoot)) return false
    val relativePattern = repositoryRoot.relativize(resolved).invariantSeparatorsPathString.restorePathSafeGlob()
    val sourceName = repositoryRoot.relativize(source).invariantSeparatorsPathString
    return if ('*' in reference || '?' in reference) globToRegex(relativePattern).matches(sourceName) else relativePattern == sourceName
  }

  private fun display(path: Path): String =
    if (path.startsWith(repositoryRoot)) repositoryRoot.relativize(path).invariantSeparatorsPathString else path.toString()
}

private open class MarkdownDocument(
  val path: Path,
  val text: String,
)

private class SpecDocument(
  path: Path,
  text: String,
  val frontmatter: SpecFrontmatter?,
) : MarkdownDocument(path, text)

private data class SpecFrontmatter(
  val values: Map<String, String>,
  val targets: List<String>,
)

private fun parseFrontmatter(text: String): SpecFrontmatter? {
  val lines = text.lines()
  if (lines.firstOrNull()?.trim() != "---") return null
  val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
  if (end < 0) return null

  val values = LinkedHashMap<String, String>()
  val targets = ArrayList<String>()
  var inTargets = false
  for (line in lines.subList(1, end + 1)) {
    val trimmed = line.trim()
    when {
      trimmed == "targets:" -> inTargets = true
      inTargets && trimmed.startsWith("- ") -> targets += trimmed.removePrefix("- ").trim().trim('"', '\'')
      line.startsWith(" ") || line.startsWith("\t") -> Unit
      else -> {
        inTargets = false
        val key = line.substringBefore(':', missingDelimiterValue = "").trim()
        if (key.isNotEmpty()) values[key] = line.substringAfter(':').trim().trim('"', '\'')
      }
    }
  }
  return SpecFrontmatter(values, targets)
}

private fun filesUnder(roots: List<Path>): List<Path> {
  val files = LinkedHashSet<Path>()
  for (root in roots) {
    if (!Files.isDirectory(root)) continue
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        files.add(file.toAbsolutePath().normalize())
        return FileVisitResult.CONTINUE
      }
    })
  }
  return files.toList()
}

private fun globToRegex(glob: String): Regex {
  val pattern = StringBuilder("^")
  var index = 0
  while (index < glob.length) {
    when {
      glob.startsWith("**/", index) -> {
        pattern.append("(?:.*/)?")
        index += 3
      }
      glob.startsWith("**", index) -> {
        pattern.append(".*")
        index += 2
      }
      glob[index] == '*' -> {
        pattern.append("[^/]*")
        index++
      }
      glob[index] == '?' -> {
        pattern.append("[^/]")
        index++
      }
      else -> {
        pattern.append(Regex.escape(glob[index].toString()))
        index++
      }
    }
  }
  return Regex(pattern.append('$').toString())
}

private fun testLinkMethodNames(text: String, afterPath: Int): List<String> {
  var open = afterPath
  while (open < text.length && text[open].isWhitespace()) open++
  if (open >= text.length || text[open] != '(') return emptyList()

  var depth = 0
  var close = -1
  for (index in open until text.length) {
    when (text[index]) {
      '(' -> depth++
      ')' -> {
        depth--
        if (depth == 0) {
          close = index
          break
        }
      }
    }
  }
  if (close < 0) return emptyList()
  val parts = text.substring(open + 1, close).split('`')
  return (1 until parts.size step 2).map { parts[it].trim() }.filter { it.isNotEmpty() }
}

private fun declaresFunction(source: String, name: String): Boolean =
  source.contains("fun `$name`") || Regex("""fun\s+${Regex.escape(name)}\s*\(""").containsMatchIn(source)

private fun lineOf(text: String, offset: Int): Int = text.take(offset).count { it == '\n' } + 1

private fun isIsoDate(value: String): Boolean {
  return try {
    LocalDate.parse(value)
    true
  }
  catch (_: DateTimeParseException) {
    false
  }
}

private fun String.toPathSafeGlob(): String = replace("*", STAR_PLACEHOLDER).replace("?", QUESTION_PLACEHOLDER)

private fun String.restorePathSafeGlob(): String = replace(STAR_PLACEHOLDER, "*").replace(QUESTION_PLACEHOLDER, "?")

private const val STAR_PLACEHOLDER = "__SPEC_STAR__"
private const val QUESTION_PLACEHOLDER = "__SPEC_QUESTION__"
private val REQUIRED_FIELDS = setOf("name", "description")
private val STATUS = Regex("""Status:\s*(.*)""")
private val DATE = Regex("""Date:\s*(\d{4}-\d{2}-\d{2})""")
private val TEST_LINK = Regex("""\[@test]\s*\(?([^\s)]+)\)?""")
private val MARKDOWN_LINK = Regex("""]\(([^)#\s]+\.md)(?:#[^)]*)?\)""")
private val BACKLINK = Regex("""(?m)^\s*//\s*@spec\s+(\S+)""")
