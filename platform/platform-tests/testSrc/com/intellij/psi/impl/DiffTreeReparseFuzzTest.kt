// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.lang.ASTNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.io.path.bufferedWriter
import kotlin.random.Random

/**
 * Compares the tree after each commit of a change in a Java file with the tree of a full parse of the new text.
 * The commit goes through [BlockSupportImpl] and merges the new tree into the old tree with `DiffTree`.
 *
 * The brute force makes each change of a list at each offset of the texts, each in a new file.
 * The fuzz test makes sequences of random changes in one file, so that the old tree of a commit is a merged tree.
 * A failure gets a short text from the minimization, and the label `REPARSE` or `MERGE`.
 * `REPARSE`: the reparsed node in place of the old node already differs from a full parse.
 * `MERGE`: only the merge of `DiffTree` makes the tree wrong.
 *
 * The trace compares two revisions, for example a branch and `master`.
 * With `difftree.reparse.trace=true`, each test writes a line for each commit to `<test name>.trace` in `TEST_UNDECLARED_OUTPUTS_DIR`.
 * The line has the case, the reparse kind, the failure flag, and a hash of the old node that each node of the new tree reuses.
 * Then it has three counts: the reused old nodes, the misplaced old nodes and the lost old nodes. See [Reuse].
 * The same seeds give the same cases, so a diff of the traces of two revisions shows each commit that merges differently.
 *
 * The settings of a long bug hunt:
 * - `difftree.reparse.texts=<directory>` adds the `.java` files of the directory up to [EXTRA_TEXT_MAX_LENGTH] chars to the texts.
 * - `difftree.reparse.hunt.wide=true` makes the brute force of the extra texts use each offset, not every [EXTRA_TEXT_STRIDE]th offset.
 * - `difftree.reparse.hunt.minutes=N` makes the fuzz test run new random sequences for N minutes.
 * - `difftree.reparse.hunt.seed=N` sets the first seed of the fuzz test.
 */
@TestApplication
class DiffTreeReparseFuzzTest {
  private val project: Project get() = projectFixture.get()

  @Test
  fun testBruteForceOfSamples(info: TestInfo) {
    bruteForce(info, "samples", SAMPLES.mapIndexed { i, text -> "sample$i" to text }, stride = 1, minNodeReparses = 1000)
  }

  @Test
  fun testBruteForceOfExtraTexts(info: TestInfo) {
    val texts = extraTexts()
    if (texts.isEmpty()) return
    bruteForce(info, "extra texts", texts, stride = if (HUNT_WIDE) 1 else EXTRA_TEXT_STRIDE, minNodeReparses = 100)
  }

  @Test
  fun testRandomEditSequences(info: TestInfo) {
    val texts = SAMPLES.mapIndexed { i, text -> "sample$i" to text } + extraTexts()
    val firstSeed = HUNT_SEED ?: if (HUNT_MINUTES > 0) Random.nextLong() else 0L
    println("fuzz: first seed $firstSeed")
    val deadline = System.nanoTime() + HUNT_MINUTES * 60_000_000_000L
    val oracle = Oracle(project, info)
    val stats = Stats()
    var round = 0
    runInEdtAndWait {
      do {
        for ((index, entry) in texts.withIndex()) {
          val (name, text) = entry
          repeat(if (name.startsWith("sample")) SAMPLE_SEQUENCES else EXTRA_TEXT_SEQUENCES) { sequence ->
            val seed = firstSeed + round * 1_000_003L + index * 1009L + sequence
            runSequence(oracle, stats, "$name, seed $seed", text, Random(seed))
          }
        }
        round++
      }
      while (System.nanoTime() < deadline)
    }
    oracle.close()
    report("fuzz", stats, minNodeReparses = 1000)
  }

  private fun runSequence(oracle: Oracle, stats: Stats, label: String, text: String, random: Random) {
    var file = oracle.createFile(text)
    if (random.nextBoolean()) oracle.dump(file)
    for (step in 0 until EDITS_PER_SEQUENCE) {
      val oldText = file.viewProvider.document!!.text
      val change = randomChange(random, oldText)
      val outcome = oracle.commit(file, change, "$label, step $step")
      stats.count(outcome.kind)
      if (outcome.failure == null) continue
      stats.addFailure(oracle, "$label, step $step", oldText, change)
      file = oracle.createFile(change.apply(oldText))
    }
  }

  private fun bruteForce(info: TestInfo, name: String, texts: List<Pair<String, String>>, stride: Int, minNodeReparses: Int) {
    val oracle = Oracle(project, info)
    val stats = Stats()
    runInEdtAndWait {
      for ((fileName, text) in texts) {
        for (offset in 0..text.length step stride) {
          for ((index, change) in changesAt(text, offset).withIndex()) {
            val outcome = oracle.commit(oracle.createFile(text), change, "$fileName:$offset:$index")
            stats.count(outcome.kind)
            if (outcome.failure != null) stats.addFailure(oracle, fileName, text, change)
          }
        }
      }
    }
    oracle.close()
    report(name, stats, minNodeReparses)
  }

  private fun report(name: String, stats: Stats, minNodeReparses: Int) {
    println("$name: ${stats.total} commits, kinds ${stats.kinds}, ${stats.failures.size} distinct failures, " +
            "${stats.notReproducedAlone} failures only after earlier changes of the sequence")
    stats.failures.values.forEach { println("FAILURE: $it\n") }
    stats.historyFailures.forEach { println("FAILURE AFTER EARLIER CHANGES: $it\n") }
    assertTrue(stats.failures.isEmpty() && stats.historyFailures.isEmpty()) {
      "$name: ${stats.failures.size + stats.historyFailures.size} failures:\n" +
      (stats.failures.values + stats.historyFailures).take(10).joinToString("\n\n")
    }
    assertTrue((stats.kinds[Kind.NODE] ?: 0) >= minNodeReparses) { "$name: too few node reparses: ${stats.kinds}" }
    assertTrue((stats.kinds[Kind.FULL] ?: 0) > 0) { "$name: no full reparse: ${stats.kinds}" }
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }
}

private val HUNT_WIDE: Boolean = System.getProperty("difftree.reparse.hunt.wide").toBoolean()
private val HUNT_MINUTES: Int = Integer.getInteger("difftree.reparse.hunt.minutes", 0)
private val HUNT_SEED: Long? = System.getProperty("difftree.reparse.hunt.seed")?.toLong()
private val TEXTS_DIRECTORY: String? = System.getProperty("difftree.reparse.texts")
private val TRACE: Boolean = System.getProperty("difftree.reparse.trace").toBoolean()

private const val EXTRA_TEXT_STRIDE = 97
private const val EXTRA_TEXT_MAX_LENGTH = 3000
private const val SAMPLE_SEQUENCES = 100
private const val EXTRA_TEXT_SEQUENCES = 1
private const val EDITS_PER_SEQUENCE = 30
private const val MAX_FAILURES = 50
private const val MINIMIZE_NANOS = 20_000_000_000L

/** The kind of the reparse of one change. */
private enum class Kind { NONE, FULL, LEAF, NODE, ERROR }

/** One change of a text: the range of the old text and the text that replaces it. */
private data class Change(val range: TextRange, val replacement: String) {
  fun apply(text: String): String = range.replace(text, replacement)
  override fun toString(): String = "$range to '${escape(replacement)}'"
}

private class Outcome(val kind: Kind, val failure: String?)

private class OldNode(val id: Int, val start: Int, val end: Int)

/**
 * The reuse of the old nodes in one commit.
 * [hash] hashes, in preorder of the new tree, the number of the old node that each node is, or -1 for a new node.
 * [reused] counts the old nodes in the new tree.
 * [misplaced] counts the reused old nodes outside the change, or around it, that are not at their old range shifted by the change.
 * [lost] counts the old nodes before or after the change that are not in the new tree.
 * The change is each placement of the edit that the old and the new text allow.
 */
private class Reuse(val hash: Long, val reused: Int, val misplaced: Int, val lost: Int)

private class Stats {
  var total = 0
  val kinds = sortedMapOf<Kind, Int>()
  val failures = LinkedHashMap<String, String>()
  val historyFailures = ArrayList<String>()
  var notReproducedAlone = 0
  private var minimizeDeadline = 0L

  fun count(kind: Kind) {
    total++
    kinds.merge(kind, 1, Int::plus)
  }

  /** Makes [change] again in a new file of [text]. If it fails there too, minimizes it. Else keeps the sequence label. */
  fun addFailure(oracle: Oracle, label: String, text: String, change: Change) {
    if (failures.size + historyFailures.size >= MAX_FAILURES) return
    val alone = oracle.commit(oracle.createFile(text), change)
    if (alone.failure == null) {
      notReproducedAlone++
      historyFailures.add("$label: change $change of '${escape(text)}'")
      return
    }
    if (minimizeDeadline == 0L) minimizeDeadline = System.nanoTime() + MINIMIZE_NANOS
    val (minText, minChange) = minimize(text, change, minimizeDeadline) { t, c -> oracle.commit(oracle.createFile(t), c).failure != null }
    val minFailure = oracle.commit(oracle.createFile(minText), minChange).failure ?: alone.failure
    val kind = oracle.failureKind(minText, minChange)
    failures.putIfAbsent("$kind:${escape(minText)}:$minChange",
                         "$kind: change $minChange in '${escape(minText)}'\nfirst seen: $label, change $change\n$minFailure")
  }
}

/**
 * Commits changes in non-physical Java files and compares the trees with full parses. Runs on the EDT.
 * With [TRACE], writes a line for each commit that has a case name.
 */
private class Oracle(private val project: Project, info: TestInfo) {
  private val documentManager = PsiDocumentManager.getInstance(project)
  private val javaFileType: FileType = FileTypeManager.getInstance().getFileTypeByExtension("java").also {
    check(it !is UnknownFileType) { "the Java plugin is not loaded" }
  }
  private val trace = if (TRACE) {
    val directory = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR") ?: System.getProperty("java.io.tmpdir")
    val path = Path.of(directory, "${info.testMethod.get().name}.trace")
    println("trace: $path")
    path.bufferedWriter()
  }
  else null

  fun createFile(text: String): PsiFileImpl =
    PsiManager.getInstance(project).findFile(LightVirtualFile("A.java", javaFileType, text)) as PsiFileImpl

  fun dump(file: PsiFileImpl): String = DebugUtil.psiToString(file, true)

  fun close() {
    trace?.close()
  }

  /**
   * Commits [change] in [file] and returns the reparse kind and the first difference from a full parse, or null.
   * With [TRACE] and a [case], writes the trace line of the commit.
   */
  fun commit(file: PsiFileImpl, change: Change, case: String? = null): Outcome {
    val document = documentManager.getDocument(file)!!
    val oldText = document.text
    val newText = change.apply(oldText)
    var kind = Kind.ERROR
    var reuse = Reuse(0, 0, 0, 0)
    val outcome = try {
      kind = kindOf(file, newText)
      val oldIds = if (trace != null && case != null) nodeIds(file.node) else null
      WriteCommandAction.runWriteCommandAction(project) {
        document.replaceString(change.range.startOffset, change.range.endOffset, change.replacement)
      }
      documentManager.commitDocument(document)
      if (oldIds != null) reuse = reuseOf(file.node, oldIds, change, oldText, newText)
      if (file.text != newText) {
        Outcome(kind, "the file text differs from the document text")
      }
      else {
        val expected = dump(createFile(newText))
        val actual = dump(file)
        Outcome(kind, if (expected == actual) null else firstDifference(expected, actual))
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      Outcome(kind, "EXCEPTION: " + e.stackTraceToString().lines().take(15).joinToString("\n"))
    }
    if (trace != null && case != null) {
      trace.append(case).append('\t').append(kind.name).append('\t').append(if (outcome.failure == null) "ok" else "FAIL")
        .append('\t').append(java.lang.Long.toHexString(reuse.hash)).append('\t').append(reuse.reused.toString())
        .append('\t').append(reuse.misplaced.toString()).append('\t').append(reuse.lost.toString()).append('\n')
    }
    return outcome
  }

  /** Numbers the nodes of [root] in preorder and keeps their ranges. A lazy node that is not parsed counts as a leaf, so the walk parses nothing. */
  private fun nodeIds(root: ASTNode): IdentityHashMap<ASTNode, OldNode> {
    val ids = IdentityHashMap<ASTNode, OldNode>()
    walk(root, 0) { node, start, end -> ids[node] = OldNode(ids.size, start, end) }
    return ids
  }

  /**
   * Compares the nodes of the new tree of [root] with the old nodes of [oldIds].
   * The same new text can come from [change] or from another place of it, such as a copy of a line above or below the line.
   * So a node counts as misplaced only if it is misplaced for each of [placements], and the lost nodes are the minimum of them.
   */
  private fun reuseOf(root: ASTNode, oldIds: IdentityHashMap<ASTNode, OldNode>, change: Change, oldText: String, newText: String): Reuse {
    val changes = placements(change, oldText, newText)
    var hash = -0x340d631b7bdddcdbL
    var reused = 0
    var misplaced = 0
    val kept = IdentityHashMap<ASTNode, Boolean>()
    walk(root, 0) { node, start, end ->
      val old = oldIds[node]
      hash = (hash xor (old?.id ?: -1).toLong()) * 0x100000001b3L
      if (old != null) {
        reused++
        kept[node] = true
        if (changes.none { isInPlace(old, start, end, it) }) misplaced++
      }
    }
    val lost = changes.minOf { c ->
      oldIds.entries.count { (node, old) -> (old.end <= c.range.startOffset || old.start >= c.range.endOffset) && node !in kept }
    }
    return Reuse(hash, reused, misplaced, lost)
  }

  /** True if [old] is at [start] and [end] in the new tree, or the place of [old] has no fixed range for [change]. */
  private fun isInPlace(old: OldNode, start: Int, end: Int, change: Change): Boolean {
    val changeStart = change.range.startOffset
    val changeEnd = change.range.endOffset
    val delta = change.replacement.length - change.range.length
    return when {
      old.end <= changeStart -> start == old.start && end == old.end
      old.start >= changeEnd -> start == old.start + delta && end == old.end + delta
      old.start <= changeStart && old.end >= changeEnd -> start == old.start && end == old.end + delta
      else -> true
    }
  }

  /** [change], the change of [oldText] to [newText] with the longest common prefix, and the change with the longest common suffix. */
  private fun placements(change: Change, oldText: String, newText: String): List<Change> {
    val minLength = minOf(oldText.length, newText.length)
    fun changeOf(prefix: Int, suffix: Int) =
      Change(TextRange(prefix, oldText.length - suffix), newText.substring(prefix, newText.length - suffix))
    val prefixFirst = oldText.commonPrefixWith(newText).length
    val prefixFirstChange = changeOf(prefixFirst, minOf(oldText.commonSuffixWith(newText).length, minLength - prefixFirst))
    val suffixFirst = oldText.commonSuffixWith(newText).length
    val suffixFirstChange = changeOf(minOf(prefixFirst, minLength - suffixFirst), suffixFirst)
    return listOf(change, prefixFirstChange, suffixFirstChange)
  }

  /** Visits [node] at [start] and its subtree in preorder. A lazy node that is not parsed counts as a leaf. */
  private fun walk(node: ASTNode, start: Int, visit: (ASTNode, Int, Int) -> Unit) {
    visit(node, start, start + node.textLength)
    if (node is LazyParseableElement && !node.isParsed) return
    var offset = start
    var child = node.firstChildNode
    while (child != null) {
      walk(child, offset, visit)
      offset += child.textLength
      child = child.treeNext
    }
  }


  private fun kindOf(file: PsiFileImpl, newText: String): Kind {
    val range = ChangedPsiRangeUtil.getChangedPsiRange(file, file.treeElement!!, newText) ?: return Kind.NONE
    val couple = BlockSupportImpl.findReparseableNodeAndReparseIt(file, file.node, range, newText) ?: return Kind.FULL
    return if (couple.first is LeafElement) Kind.LEAF else Kind.NODE
  }

  /** `REPARSE` if the reparsed node in place of the old node differs from a full parse, else `MERGE`. */
  fun failureKind(text: String, change: Change): String {
    val file = createFile(text)
    dump(file)
    val newText = change.apply(text)
    val range = ChangedPsiRangeUtil.getChangedPsiRange(file, file.treeElement!!, newText) ?: return "MERGE (no change)"
    val couple = BlockSupportImpl.findReparseableNodeAndReparseIt(file, file.node, range, newText) ?: return "MERGE (full reparse)"
    val inPlace = dumpTree(file.node, couple.first, couple.second)
    val full = dumpTree(createFile(newText).node, null, null)
    return if (inPlace == full) "MERGE (node reparse of ${couple.first.elementType})" else "REPARSE of ${couple.first.elementType}"
  }
}

/** Dumps the tree of [root] with [newNode] in place of [oldNode]: the type, the PSI class, the error, the range and the leaf text of each node. */
private fun dumpTree(root: ASTNode, oldNode: ASTNode?, newNode: ASTNode?): String = buildString {
  fun visit(node: ASTNode, start: Int, depth: Int): Int {
    val current = if (node === oldNode) newNode!! else node
    append("  ".repeat(depth)).append(current.elementType).append(' ').append(node.psi?.javaClass?.simpleName)
    if (current is PsiErrorElement) append(" '").append(current.errorDescription).append('\'')
    var child = current.firstChildNode
    if (child == null) {
      val length = current.textLength
      append(' ').append(start).append('-').append(start + length).append(" '").append(escape(current.text)).append("'\n")
      return start + length
    }
    append('\n')
    var offset = start
    while (child != null) {
      offset = visit(child, offset, depth + 1)
      child = child.treeNext
    }
    return offset
  }
  visit(root, 0, 0)
}

/** The changes at one offset: each insertion, the deletions, and at a line start a copy and a deletion of the line. */
private fun changesAt(text: String, offset: Int): List<Change> {
  val changes = ArrayList<Change>()
  INSERTIONS.mapTo(changes) { Change(TextRange(offset, offset), it) }
  for (length in intArrayOf(1, 2, 5)) {
    if (offset + length <= text.length) changes.add(Change(TextRange(offset, offset + length), ""))
  }
  if (offset == 0 || offset <= text.length && text[offset - 1] == '\n') {
    val lineEnd = lineEnd(text, offset)
    if (lineEnd > offset) {
      changes.add(Change(TextRange(offset, offset), text.substring(offset, lineEnd)))
      changes.add(Change(TextRange(offset, lineEnd), ""))
      val nextEnd = lineEnd(text, lineEnd(text, lineEnd))
      if (nextEnd > lineEnd) changes.add(Change(TextRange(offset, offset), text.substring(offset, nextEnd)))
    }
  }
  return changes
}

private fun lineEnd(text: String, offset: Int): Int = text.indexOf('\n', offset).let { if (it < 0) text.length else it + 1 }

private fun lineStarts(text: String): List<Int> = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }

/** A random edit: typing, a deletion, a copy or a deletion of lines, a swap of two lines, or a paste of a part of the text. */
private fun randomChange(random: Random, text: String): Change {
  val offset = random.nextInt(text.length + 1)
  val lines = lineStarts(text)
  return when (random.nextInt(10)) {
    0, 1, 2 -> Change(TextRange(offset, offset), INSERTIONS.random(random))
    3, 4 -> Change(TextRange(offset, minOf(text.length, offset + 1 + random.nextInt(10))), "")
    5, 6 -> {
      val start = lines.random(random)
      var end = start
      repeat(1 + random.nextInt(4)) { end = lineEnd(text, end) }
      val copy = text.substring(start, end).let { if (it.endsWith('\n') || it.isEmpty()) it else "\n$it" }
      if (copy.startsWith('\n')) Change(TextRange(end, end), copy) else Change(TextRange(start, start), copy)
    }
    7 -> {
      val start = lines.random(random)
      Change(TextRange(start, lineEnd(text, lineEnd(text, start))), "")
    }
    8 -> {
      val start = lines.random(random)
      val middle = lineEnd(text, start)
      val end = lineEnd(text, middle)
      val first = text.substring(start, middle)
      val second = text.substring(middle, end)
      if (!first.endsWith('\n') || !second.endsWith('\n')) Change(TextRange(offset, offset), " ")
      else Change(TextRange(start, end), second + first)
    }
    else -> {
      val from = random.nextInt(text.length + 1)
      val to = minOf(text.length, from + random.nextInt(120))
      val replaced = if (random.nextBoolean()) 0 else minOf(text.length - offset, random.nextInt(20))
      Change(TextRange(offset, offset + replaced), text.substring(from, to))
    }
  }
}

/** Removes whole lines, then chars, outside of the change while [fails] stays true, until [deadline] of [System.nanoTime]. */
private fun minimize(text: String, change: Change, deadline: Long, fails: (String, Change) -> Boolean): Pair<String, Change> {
  var currentText = text
  var currentRange = change.range

  fun tryRemove(start: Int, end: Int): Boolean {
    val overlaps = start < currentRange.endOffset && end > currentRange.startOffset ||
                   start == currentRange.startOffset && currentRange.isEmpty
    if (overlaps) return false
    val candidate = currentText.removeRange(start, end)
    val candidateRange = if (end <= currentRange.startOffset) currentRange.shiftLeft(end - start) else currentRange
    if (!fails(candidate, Change(candidateRange, change.replacement))) return false
    currentText = candidate
    currentRange = candidateRange
    return true
  }

  var changed = true
  while (changed && System.nanoTime() < deadline) {
    changed = false
    var lineStart = 0
    while (lineStart < currentText.length && System.nanoTime() < deadline) {
      val end = lineEnd(currentText, lineStart)
      if (tryRemove(lineStart, end)) changed = true else lineStart = end
    }
    for (length in intArrayOf(8, 4, 2, 1)) {
      var start = 0
      while (start + length <= currentText.length && System.nanoTime() < deadline) {
        if (tryRemove(start, start + length)) changed = true else start++
      }
    }
  }
  return currentText to Change(currentRange, change.replacement)
}

private fun firstDifference(expected: String, actual: String): String {
  val expectedLines = expected.lines()
  val actualLines = actual.lines()
  val index = expectedLines.indices.firstOrNull { expectedLines[it] != actualLines.getOrNull(it) } ?: expectedLines.size
  val from = maxOf(0, index - 3)
  return "expected:\n" + expectedLines.subList(from, minOf(expectedLines.size, index + 4)).joinToString("\n") +
         "\nactual:\n" + actualLines.subList(minOf(from, actualLines.size), minOf(actualLines.size, index + 4)).joinToString("\n")
}

private fun escape(text: CharSequence): String = text.toString().replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")

/** The `.java` files of [TEXTS_DIRECTORY] up to [EXTRA_TEXT_MAX_LENGTH] chars, or no texts without the setting. */
private fun extraTexts(): List<Pair<String, String>> {
  val root = File(TEXTS_DIRECTORY ?: return emptyList())
  val files = root.walkTopDown().filter { it.isFile && it.extension == "java" }.sortedBy { it.path }.toList()
  check(files.isNotEmpty()) { "no Java files in $root" }
  val seen = HashSet<String>()
  return files.map { it.relativeTo(root).path to it.readText().replace("\r\n", "\n") }
    .filter { it.second.length in 1..EXTRA_TEXT_MAX_LENGTH && seen.add(it.second) }
}

private val INSERTIONS = listOf(
  "x", " ", "\n", ";", "{", "}", "(", ")", ",", "<", ">", "=", ".", "@A ", "\"", "'", "/*", "*/", "//", "/**", "\\",
  "a();", "int a = 1;", "{\n}", "\n}\n", "class B {}", "void m() {}", "\"\"\"\n", "->", "case 1:", "\u00e9",
)

/** Samples with many equal children: statements, fields, enum constants, array items, annotations and members. */
private val SAMPLES = listOf(
  """
  |class A {
  |  void foo() {
  |    a();
  |    a();
  |    a();
  |    int a = 1;
  |    int a = 1;
  |    int a = 1;
  |    if (b) { a(); } else { a(); }
  |    if (b) { a(); } else { a(); }
  |    for (int i = 0; i < 10; i++) { a(); a(); }
  |    Runnable r = () -> { a(); a(); };
  |    a();
  |    a();
  |  }
  |  void foo() {
  |    a();
  |    a();
  |  }
  |}
  |""".trimMargin(),
  """
  |@A @A @A
  |class B {
  |  int x = 1;
  |  int x = 1;
  |  int x = 1;
  |  /** doc */
  |  int y;
  |  /** doc */
  |  int y;
  |  int[] z = {1, 1, 1, 1, 1, 2, 1, 1};
  |  enum E { A, A, B, A, A; void m() { } }
  |  class C { void m() { a(); } }
  |  class C { void m() { a(); } }
  |  String s = "a" + "a" + "a";
  |  String t = ""${'"'}
  |    text
  |    ""${'"'};
  |}
  |""".trimMargin(),
  """
  |import a.b;
  |import a.b;
  |import a.c.*;
  |
  |public class C extends D implements E, E {
  |  static { a(); a(); }
  |  { a(); }
  |  C() { this(1); }
  |  <T> T m(T t) throws X, X { return switch (t) { case 1 -> a(); case 1 -> a(); default -> { yield b; } }; }
  |  Object o = new Object() { void m() { a(); } void m() { a(); } };
  |  // comment
  |  // comment
  |  void n() {
  |    try { a(); } catch (X | X e) { a(); } finally { a(); }
  |    label: while (true) { break label; }
  |    synchronized (this) { a(); }
  |    a(); // comment
  |    a(); // comment
  |  }
  |}
  |""".trimMargin(),
)
