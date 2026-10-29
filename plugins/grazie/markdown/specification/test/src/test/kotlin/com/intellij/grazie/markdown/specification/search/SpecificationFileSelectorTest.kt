package com.intellij.grazie.markdown.specification.search

import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpecificationFileSelectorTest {
  @Test
  fun `finds files with shared rare terms`() {
    val root = document("create-contrib-module/SKILL.md", """
      # Create a JPS module

      Use the JPS module registration helper to register the `.iml` file.
      Do not edit `modules.xml` by hand.
      Keep the canonical ordering rule.
    """.trimIndent())
    val moduleDependencies = document("module-dependencies/SKILL.md", """
      # JPS module dependencies

      Register a new JPS module with `jps-module.mjs`.
      The helper updates the `.iml` file and `modules.xml`.
    """.trimIndent())
    val repositoryRules = document("CLAUDE.md", """
      # Repository rules

      Registration uses the helper for a JPS module.
      Keep the canonical ordering rule for the module entry.
    """.trimIndent())
    val unrelated = document("spellcheck/SKILL.md", """
      # Spell checking

      Run grammar checks on Markdown documents.
      Update language dictionaries when needed.
    """.trimIndent())

    val similar = SpecificationFileSelector.findSimilar(
      root = root,
      candidates = listOf(root, moduleDependencies, repositoryRules, unrelated),
      documentFrequencies = frequencies(root),
    )

    assertEquals(listOf(repositoryRules.path, moduleDependencies.path), similar.map { it.path })
  }

  @Test
  fun `keeps only the longest terms`() {
    val longTerms = List(SpecificationFileSelector.MAX_TERMS_PER_DOCUMENT) { "longterm$it" }
    val document = document("root.md", (List(10) { "short$it" } + longTerms + "longtermx").joinToString(" "))

    // "longtermx" has the length of "longterm0", but it is last in the alphabetical order.
    assertEquals(longTerms.toSet(), document.terms.keys)
  }

  @Test
  fun `does not find a document that shares only short words`() {
    val shortWords = List(10) { "short$it" }
    val root = document("root.md", (shortWords + List(50) { "rootlongterm$it" }).joinToString(" "))
    val other = document("other.md", (shortWords + List(50) { "otherlongterm$it" }).joinToString(" "))

    val similar = SpecificationFileSelector.findSimilar(root, listOf(other), frequencies(root) + frequencies(other))

    assertEquals(emptyList<String>(), similar.map { it.path })
  }

  @Test
  fun `uses a fixed score threshold`() {
    val root = document("root.md", "${"alpha ".repeat(100)}beta gamma delta epsilon zeta eta theta iota kappa")
    val strong = document("strong.md", rootText(root))
    val weak = document("weak.md", "alpha ${"beta ".repeat(100)}gamma delta epsilon zeta eta theta iota kappa")

    val similar = SpecificationFileSelector.findSimilar(root, listOf(strong, weak), frequencies(root))

    assertEquals(listOf(strong.path, weak.path), similar.map { it.path })
  }

  @Test
  fun `does not use terms that are not rare`() {
    val root = document("root.md", "alpha beta")
    val other = document("similar.md", "alpha beta")

    val similar = SpecificationFileSelector.findSimilar(root, listOf(other), mapOf("alpha" to 201, "beta" to 201))

    assertEquals(emptyList<String>(), similar.map { it.path })
  }

  @Test
  fun `does not give maximum score to a single shared term`() {
    val root = document("root.md", (listOf("shared") + List(100) { "rootterm$it" }).joinToString(" "))
    val different = document("different.md", (listOf("shared") + List(100) { "differentterm$it" }).joinToString(" "))

    val similar = SpecificationFileSelector.findSimilar(root, listOf(different), frequencies(root))

    assertEquals(emptyList<String>(), similar.map { it.path })
  }

  @Test
  fun `result does not depend on candidate order`() {
    val root = document("root.md", "module registration helper")
    val first = document("first.md", "module registration helper")
    val second = document("second.md", "module registration")
    val third = document("third.md", "unrelated text")

    fun findSimilar(candidates: List<SpecificationFileSelector.IndexedDocument>) =
      SpecificationFileSelector.findSimilar(root, candidates, frequencies(root)).map { it.path }

    assertEquals(findSimilar(listOf(first, second, third)), findSimilar(listOf(third, second, first)))
  }

  private fun document(path: String, text: String): SpecificationFileSelector.IndexedDocument =
    SpecificationFileSelector.index(path, text)

  private fun rootText(document: SpecificationFileSelector.IndexedDocument): String =
    document.terms.entries.joinToString(" ") { (term, frequency) -> List(frequency) { term }.joinToString(" ") }

  private fun frequencies(root: SpecificationFileSelector.IndexedDocument): Map<String, Int> =
    root.terms.keys.associateWith { 1 }
}
