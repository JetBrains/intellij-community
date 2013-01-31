Feature: Git Cherry-Pick When Auto-Commit is deselected

  Background:
    Given disabled auto-commit in the settings
    Given new committed files file.txt, a.txt, conflict.txt with initial content
    Given branch feature

    Given commit f5027a3 on branch feature
      """
      fix #1
      Author: John Bro
      M file.txt "feature changes"
      """

  Scenario: Simple cherry-pick
    When I cherry-pick the commit f5027a3
    Then commit dialog should be shown
    And active changelist is 'fix #1 (cherry picked from commit f5027a3)'


  Scenario: Simple cherry-pick, agree to commit
    When I cherry-pick the commit f5027a3 and commit
    Then the last commit is
      """
      fix #1
      (cherry picked from commit f5027a3)
      """
    And success notification is shown 'Cherry-pick successful'
       """
       f5027a3 fix #1
       """
    And no new changelists are created

  Scenario: Simple cherry-pick, cancel commit
    When I cherry-pick the commit f5027a3 and don't commit
    Then nothing is committed
    And active changelist is 'fix #1 (cherry picked from commit f5027a3)'
    And no notification is shown

  Scenario: Cherry-pick 2 commits
    Given commit abc1234 on branch feature
      """
      fix #2
      M file.txt "more feature changes"
      """
    When I cherry-pick commits f5027a3, abc1234 and commit both of them
    Then `git log -2` should return
      """
      fix #2
      (cherry picked from commit abc1234)
      -----
      fix #1
      (cherry picked from commit f5027a3)
      """
    And no new changelists are created

  Scenario: Cherry-pick 2 commits, cancel committing the second
    Given commit abc1234 on branch feature
      """
      fix #2
      M file.txt "more feature changes"
      """
    When I cherry-pick commits f5027a3, abc1234, but commit only the first one
    Then the last commit is
      """
      fix #1
      (cherry picked from commit f5027a3)
      """
    And working tree is dirty
    And active changelist is 'fix #2 (cherry picked from commit abc1234)'
    And warning notification is shown 'Cherry-pick cancelled'
      """
      abc1234 fix #2
      <hr/>
      However cherry-pick succeeded for the following commit:
      f5027a3 fix #1
      """

  Scenario: Dirty tree, conflicting with the commit
    Given file.txt is locally modified:
      """
      master content
      """
    When I cherry-pick the commit f5027a3
    Then nothing is committed
    And error notification is shown 'Cherry-pick failed'
      """
      f5027a3 fix #1
      Your local changes would be overwritten by cherry-pick.
      Commit your changes or stash them to proceed.
      """

  Scenario: Untracked files, conflicting with cherry-picked commit
    Given commit aff6453 on branch feature
      """
      add file
      A untracked.txt "feature changes"
      """
    Given file untracked.txt 'master changes'
    When I cherry-pick the commit aff6453
    Then no new changelists are created
    And error notification is shown 'Cherry-pick failed'
      """
      aff6453 add file
      Some untracked working tree files would be overwritten by cherry-pick.
      Please move, remove or add them before you can cherry-pick. <a href='view'>View them</a>
      """

  Scenario: Conflict with cherry-picked commit should show merge dialog
    Given commit aff6453 on branch master
      """
      master content
      M conflict.txt "master version"
      """
    Given commit bb6453c on branch feature
      """
      feature content
      M conflict.txt "feature version"
      """
    When I cherry-pick the commit bb6453c
    Then merge dialog should be shown

  Scenario: Resolve conflict and agree to commit
    Given commit aff6453 on branch master
      """
      master content
      M conflict.txt "master version"
      """
    Given commit bb6453c on branch feature
      """
      feature content
      M conflict.txt "feature version"
      """
    When I cherry-pick the commit bb6453c, resolve conflicts and commit
    Then the last commit is
      """
      feature content
      (cherry picked from commit bb6453c)
      """
    And success notification is shown 'Cherry-pick successful'
      """
      bb6453c feature content
      """
    And no new changelists are created
