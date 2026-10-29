// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.walk

/**
 * Checks the links between the specs, the decision records (ADRs) and the sources of one feature.
 *
 * The feature keeps its specs in `spec/` and its ADRs in `adr/` under [featureRoot]. An ADR file is named
 * `NNNN-<slug>.md`. The checker reports:
 * - a relative Markdown link whose target does not exist;
 * - a link with the text `ADR NNNN` or `NNNN` whose target is not the ADR with that number;
 * - an `ADR NNNN` mention in a spec or an ADR that is not a link;
 * - a spec `targets` entry or a `[@test]` path that does not exist. A `targets` entry can be a glob, which must
 *   match at least one file;
 * - a `// @spec` backlink in a source file that does not point to an existing `*.spec.md` file;
 * - an `ADR NNNN` mention in a source file for which no ADR exists.
 *
 * The checker never requires a link to be present. It checks only the links that exist. Fenced code blocks and
 * inline code spans in Markdown are not checked.
 *
 * A target outside [checkedRoot] is skipped. Pass the root of the checked-out repository, so a run from a smaller
 * checkout skips the links that go to the part that is not there.
 *
 * @param featureRoot the folder that holds `spec/` and `adr/`.
 * @param checkedRoot the folder outside which a link target is not checked.
 * @param sourceRoots the folders whose Kotlin and Java files are checked for backlinks and ADR mentions.
 * @param excludedSources the files and folders under [sourceRoots] that are not checked, such as the tests of this
 *   checker, which hold broken links on purpose.
 * @param extraMarkdown other Markdown files of the feature, such as `AGENTS.md`. Their links are checked, but a plain
 *   `ADR NNNN` mention in them is allowed.
 */
class DocLinkChecker(
  private val featureRoot: Path,
  private val checkedRoot: Path,
  private val sourceRoots: List<Path>,
  private val excludedSources: List<Path> = emptyList(),
  private val extraMarkdown: List<Path> = emptyList(),
) {
  private val specDir = featureRoot.resolve(SPEC_DIR)
  private val adrDir = featureRoot.resolve(ADR_DIR)
  private val adrNumbers: Set<String> by lazy { adrFiles().mapNotNullTo(HashSet()) { adrNumber(it) } }

  /** Returns every problem in the specs, the ADRs, the extra Markdown files and the sources, sorted by file and line. */
  fun findProblems(): List<DocProblem> {
    val docs = specFiles().flatMap { checkMarkdown(it, requireLinkedMentions = true) } +
               adrFiles().flatMap { checkMarkdown(it, requireLinkedMentions = true) } +
               extraMarkdown.filter { it.isRegularFile() }.flatMap { checkMarkdown(it, requireLinkedMentions = false) }
    return (docs + sourceFiles().flatMap { checkSource(it) }).sortedWith(compareBy({ it.file.toString() }, { it.line }))
  }

  private fun specFiles(): List<Path> =
    if (specDir.isDirectory()) specDir.walk().filter { it.extension == "md" }.sorted().toList() else emptyList()

  private fun adrFiles(): List<Path> =
    if (adrDir.isDirectory()) adrDir.listDirectoryEntries("*.md").sorted() else emptyList()

  private fun sourceFiles(): List<Path> =
    sourceRoots.filter { it.isDirectory() }.flatMap { root ->
      root.walk().filter { isCheckedSource(it) }.sorted().toList()
    }

  private fun isCheckedSource(file: Path): Boolean =
    file.isRegularFile() && file.extension in SOURCE_EXTENSIONS && excludedSources.none { file.startsWith(it) }

  private fun checkMarkdown(file: Path, requireLinkedMentions: Boolean): List<DocProblem> {
    val text = readDocText(file)
    val prose = withoutCode(text)
    val problems = mutableListOf<DocProblem>()
    for (link in MARKDOWN_LINK.findAll(prose)) {
      val (rawText, target) = link.destructured
      val linkText = rawText.replace(WHITESPACE, " ").trim()
      val line = lineOf(text, link.range.first)
      if (isExternal(target)) continue
      val path = target.substringBefore('#')
      if (path.isEmpty()) continue
      val resolved = file.parent.resolve(path).normalize()
      if (!isChecked(resolved)) continue
      if (!Files.exists(resolved)) {
        problems += DocProblem(file, line, "the link target $target does not exist")
        continue
      }
      val linkedNumber = ADR_LINK_TEXT.matchEntire(linkText)?.groupValues?.get(1) ?: continue
      if (adrNumber(resolved) != linkedNumber) {
        problems += DocProblem(file, line, "the link [$linkText] points to ${resolved.name}, not to ADR $linkedNumber")
      }
    }
    if (requireLinkedMentions) {
      for (mention in ADR_MENTION.findAll(blank(prose, MARKDOWN_LINK))) {
        problems += DocProblem(file, lineOf(text, mention.range.first), "the mention ${mention.value} is not a link")
      }
    }
    for ((path, offset) in specTargets(text) + TEST_LINK.findAll(prose).map { it.groupValues[1] to it.range.first }) {
      if (!pathExists(file.parent, path)) {
        problems += DocProblem(file, lineOf(text, offset), "the path $path does not exist")
      }
    }
    return problems
  }

  private fun checkSource(file: Path): List<DocProblem> {
    val text = readDocText(file)
    val problems = mutableListOf<DocProblem>()
    for (backlink in SPEC_BACKLINK.findAll(text)) {
      val path = backlink.groupValues[1]
      val resolved = file.parent.resolve(path).normalize()
      val line = lineOf(text, backlink.range.first)
      when {
        !isChecked(resolved) -> Unit
        !resolved.name.endsWith(SPEC_SUFFIX) -> problems += DocProblem(file, line, "the backlink $path is not a spec")
        !Files.exists(resolved) -> problems += DocProblem(file, line, "the backlink $path does not exist")
      }
    }
    for (mention in ADR_MENTION.findAll(text)) {
      if (mention.groupValues[1] !in adrNumbers) {
        problems += DocProblem(file, lineOf(text, mention.range.first), "no ADR exists for the mention ${mention.value}")
      }
    }
    return problems
  }

  /** Returns each `targets` entry of the front matter of a spec, with its offset in [text]. */
  private fun specTargets(text: String): List<Pair<String, Int>> {
    val frontMatter = FRONT_MATTER.find(text) ?: return emptyList()
    val targets = TARGETS_BLOCK.find(frontMatter.value) ?: return emptyList()
    return TARGET_ENTRY.findAll(targets.value).map {
      it.groupValues[1].trim('"', '\'') to frontMatter.range.first + targets.range.first + it.range.first
    }.toList()
  }

  /**
   * Returns `true` when [path], relative to [dir], names an existing file or folder, or when it is outside
   * [checkedRoot]. A glob path must match at least one file.
   */
  private fun pathExists(dir: Path, path: String): Boolean {
    val segments = path.split('/')
    val globStart = segments.indexOfFirst { segment -> segment.any { it in GLOB_CHARS } }
    if (globStart < 0) {
      val resolved = dir.resolve(path).normalize()
      return !isChecked(resolved) || Files.exists(resolved)
    }
    val base = dir.resolve(segments.subList(0, globStart).joinToString("/")).normalize()
    if (!isChecked(base)) return true
    if (!base.isDirectory()) return false
    val matcher = base.fileSystem.getPathMatcher("glob:" + segments.subList(globStart, segments.size).joinToString("/"))
    return base.walk().any { matcher.matches(base.relativize(it)) }
  }

  private fun isChecked(path: Path): Boolean = path.startsWith(checkedRoot)

  companion object {
    /** Holds the name of the folder with the specs of a feature. */
    const val SPEC_DIR: String = "spec"

    /** Holds the name of the folder with the decision records of a feature. */
    const val ADR_DIR: String = "adr"

    private const val SPEC_SUFFIX = ".spec.md"
    private val SOURCE_EXTENSIONS = setOf("kt", "java")
    private val GLOB_CHARS = setOf('*', '?', '[', '{')

    private val MARKDOWN_LINK = Regex("""\[([^]]*)]\(([^)\s]+)\)""")
    private val ADR_LINK_TEXT = Regex("""(?:ADR )?(\d{4})""")
    private val ADR_MENTION = Regex("""\bADR (\d{4})\b""")
    private val TEST_LINK = Regex("""\[@test]\s+(\S+)""")
    private val SPEC_BACKLINK = Regex("""//\s*@spec\s+(\S+)""")
    private val FRONT_MATTER = Regex("""\A---\n.*?\n---\n""", RegexOption.DOT_MATCHES_ALL)
    private val TARGETS_BLOCK = Regex("""^targets:\n(?:[ \t]+-.*\n)*""", RegexOption.MULTILINE)
    private val TARGET_ENTRY = Regex("""^[ \t]+-[ \t]+(\S+)""", RegexOption.MULTILINE)
    private val WHITESPACE = Regex("""\s+""")
    private val ADR_FILE = Regex("""(\d{4})-.*\.md""")

    /** Returns the number of the ADR file [file], or `null` when its name is not `NNNN-<slug>.md`. */
    fun adrNumber(file: Path): String? = ADR_FILE.matchEntire(file.name)?.groupValues?.get(1)

    private fun isExternal(target: String): Boolean = target.contains("://") || target.startsWith("mailto:")
  }
}
