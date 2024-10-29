// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

class GitBranchesTreeSelectionTest: GitBranchesTreeTest() {
  fun `test another branch is not selected if current matches search field`() = branchesTreeTest {
    setState(localBranches = listOf("main-123", "main"), remoteBranches = listOf("main"))
    selectBranch("main-123")

    // Remote node collapsed
    val expectedBeforeTyping = """
      |-ROOT
      | HEAD
      | -LOCAL
      |  BRANCH:main
      |  [BRANCH:main-123]
      | +REMOTE
    """.trimMargin()
    assertTree(expectedBeforeTyping)

    // Remote node expanded
    val expectedDuringTyping = """
      |-ROOT
      | HEAD
      | -LOCAL
      |  BRANCH:main
      |  [BRANCH:main-123]
      | -REMOTE
      |  -REMOTE:origin
      |   BRANCH:origin/main
    """.trimMargin()

    "mai".toCharArray().forEach { char ->
      searchTextField.text += char
      assertTree(expectedDuringTyping)
    }
  }

  fun `test selection is changed to match search field`() = branchesTreeTest {
    setState(localBranches = listOf("1", "2", "3", "main"), remoteBranches = listOf("main"))
    selectBranch("1")

    searchTextField.text = "main"
    assertTree("""
      |-ROOT
      | HEAD
      | -LOCAL
      |  [BRANCH:main]
      | -REMOTE
      |  -REMOTE:origin
      |   BRANCH:origin/main
    """.trimMargin())
  }

  fun `test selection is updated on exact match`() = branchesTreeTest {
    setState(localBranches = listOf("main-123", "main"), remoteBranches = listOf("main"))
    selectBranch("main-123")

    searchTextField.text = "mai"
    val expectedSelectionNotUpdated = """
      |-ROOT
      | HEAD
      | -LOCAL
      |  BRANCH:main
      |  [BRANCH:main-123]
      | -REMOTE
      |  -REMOTE:origin
      |   BRANCH:origin/main
    """.trimMargin()
    assertTree(expectedSelectionNotUpdated)

    searchTextField.text = "main"
    val expectedSelectionUpdated = """
      |-ROOT
      | HEAD
      | -LOCAL
      |  [BRANCH:main]
      |  BRANCH:main-123
      | -REMOTE
      |  -REMOTE:origin
      |   BRANCH:origin/main
    """.trimMargin()
    assertTree(expectedSelectionUpdated)
  }

  fun `test exact match of branch name and group node`() = branchesTreeTest {
    setState(localBranches = listOf("main/123", "main"), remoteBranches = listOf())
    selectBranch("main/123")
    searchTextField.text = "main"
    assertTree("""
      |-ROOT
      | HEAD
      | -LOCAL
      |  -GROUP:main
      |   BRANCH:main/123
      |  [BRANCH:main]
    """.trimMargin())
  }

  fun `test empty groups are not shown`() = branchesTreeTest {
    setState(localBranches = listOf("group-1/match", "group-2/qq"), remoteBranches = listOf())
    searchTextField.text = "match"
    assertTree("""
      |-ROOT
      | HEAD
      | -LOCAL
      |  -GROUP:group-1
      |   [BRANCH:group-1/match]
    """.trimMargin())
  }

  fun `test selection of remote`() = branchesTreeTest {
    setState(localBranches = listOf("main"), remoteBranches = listOf("main", "ish/242", "a/242/b", "242", "242/fix"))

    searchTextField.text = "242"
    assertTree("""
      |-ROOT
      | HEAD
      | LOCAL
      | -REMOTE
      |  -REMOTE:origin
      |   -GROUP:242
      |    BRANCH:origin/242/fix
      |   -GROUP:a
      |    -GROUP:242
      |     BRANCH:origin/a/242/b
      |   -GROUP:ish
      |    BRANCH:origin/ish/242
      |   [BRANCH:origin/242]
    """.trimMargin())
  }

  fun `test selection of remote with no grouping`() = branchesTreeTest(groupByDirectories = false) {
    setState(localBranches = listOf("main"), remoteBranches = listOf("main", "ish/242", "a/242/b", "242", "242/fix"))

    searchTextField.text = "242"
    assertTree("""
     |-ROOT
     | HEAD
     | LOCAL
     | -REMOTE
     |  [BRANCH:origin/242]
     |  BRANCH:origin/242/fix
     |  BRANCH:origin/a/242/b
     |  BRANCH:origin/ish/242
    """.trimMargin())
  }

  fun `test no selection when no match`() = branchesTreeTest(groupByDirectories = false) {
    setState(localBranches = listOf("main"), remoteBranches = listOf("main", "ish/242", "a/242/b", "242", "242/fix"))

    searchTextField.text = "not-main"
    assertTrue(branchesTree.isEmptyModel())
    assertTree("""
     |-ROOT
     | HEAD
     | LOCAL
     | REMOTE
    """.trimMargin())

    searchTextField.text = ""
    assertFalse(branchesTree.isEmptyModel())
  }

  fun `test tag can be matched`() = branchesTreeTest(groupByDirectories = false) {
    setState(localBranches = listOf("main"), remoteBranches = listOf("main"), tags = listOf("ma"))

    searchTextField.text = "ma"
    assertTree("""
      |-ROOT
      | HEAD
      | -LOCAL
      |  BRANCH:main
      | -REMOTE
      |  BRANCH:origin/main
      | -TAG
      |  [TAG:ma]
    """.trimMargin())
  }
}