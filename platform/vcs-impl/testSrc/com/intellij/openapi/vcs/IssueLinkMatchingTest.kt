// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IssueLinkMatchingTest {
  private var _issueNavigationConfiguration: IssueNavigationConfiguration? = null
  private val issueNavigationConfiguration get() = _issueNavigationConfiguration!!

  @Before
  fun before() {
    _issueNavigationConfiguration = IssueNavigationConfiguration()
  }

  fun after() {
    _issueNavigationConfiguration = null
  }

  @Test
  fun `containing matches 1`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("abc\\d{2}", "http://example.com/$0"),
      IssueNavigationLink("bc\\d{1}", "http://example.com/$0"),
    )
    val source = "fixes abc22 issue"
    assertEquals(listOf("abc22"), getIssueMatches(source))
  }

  @Test
  fun `containing matches 2`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("bc\\d{1}", "http://example.com/$0"),
      IssueNavigationLink("abc\\d{2}", "http://example.com/$0"),
    )
    val source = "fixes abc22 issue"
    assertEquals(listOf("abc22"), getIssueMatches(source))
  }

  @Test
  fun `intersecting matches 1`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("[A-Z]+\\-\\d+", "http://example.com/$0"),
      IssueNavigationLink("\\d+:", "http://example.com/$0"),
    )
    val source = "fixes ABC-239: issue"
    assertEquals(listOf("ABC-239"), getIssueMatches(source))
  }

  @Test
  fun `intersecting matches 2`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("(\\d+):", "http://example.com/$1"),
      IssueNavigationLink("[A-Z]+\\-\\d+", "http://example.com/$0"),
    )
    val source = "fixes ABC-239: issue"
    assertEquals(listOf("ABC-239"), getIssueMatches(source))
  }

  @Test
  fun `concatenated matches`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("[A-Z]+\\-\\d+", "http://example.com/$0"),
    )
    val source = "fixes ABC-239CDE-155 issue"
    assertEquals(listOf("ABC-239", "CDE-155"), getIssueMatches(source))
  }

  @Test
  fun `simple matches`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("^(\\d+):", "http://example.com/$1"),
      IssueNavigationLink("[A-Z]+\\-\\d+", "http://example.com/$0"),
    )
    val source = "239: fixed stuff, also mention CDE-666 problem"
    assertEquals(listOf("239:", "CDE-666"), getIssueMatches(source))
  }

  @Test
  fun `equal range matches -- pick last`() {
    issueNavigationConfiguration.links = listOf(
      IssueNavigationLink("[A-Z]+\\-\\d+", "http://d1.com/$0"),
      IssueNavigationLink("ABC\\-(\\d+)", "http://d2.com/$1"),
    )
    val source = "fixes ABC-23 issue"
    assertEquals(listOf("http://d2.com/23"), getIssueUrls(source))
  }

  private fun getIssueMatches(source: String) = issueNavigationConfiguration.findIssueLinks(source).map { it.range.substring(source) }
  private fun getIssueUrls(source: String) = issueNavigationConfiguration.findIssueLinks(source).map { it.targetUrl }
}