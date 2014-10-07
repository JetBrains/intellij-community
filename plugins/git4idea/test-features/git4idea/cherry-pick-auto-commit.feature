Feature: Git Cherry-Pick When Auto-Commit is selected

Background:
  Given enabled auto-commit in the settings
  Given new committed files file.txt, a.txt, conflict.txt with initial content
  Given branch feature
  Given commit f5027a3 on branch feature
    """
    fix #1
    Author: John Bro
    M file.txt "initial content\nfeature changes"
    """

  Scenario: Simple cherry-pick
    When I cherry-pick the commit f5027a3
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

  Scenario: Unresolved conflict with cherry-picked commit should produce a changelist
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
    When I cherry-pick the commit bb6453c and don't resolve conflicts
    Then there is changelist 'feature content (cherry picked from commit bb6453c)'
    And warning notification is shown 'Cherry-picked with conflicts'
      """
      bb6453c feature content
      Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>
      """

  Scenario: Resolved conflict should show commit dialog
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
    When I cherry-pick the commit bb6453c and resolve conflicts
    Then commit dialog should be shown

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
      bb6453c feature content"""
    And no new changelists are created

  Scenario: Resolve conflict, but cancel commit
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
    When I cherry-pick the commit bb6453c, resolve conflicts and don't commit
    Then there is changelist 'feature content (cherry picked from commit bb6453c)'
    And no notification is shown

  Scenario: Cherry-pick 2 commits
    Given commit c123abc on branch feature
      """
      fix #2
      M file.txt "feature changes\nmore feature changes"
      """
    When I cherry-pick commits f5027a3 and c123abc
    Then `git log -2` should return
      """
      fix #2

      (cherry picked from commit c123abc)
      -----
      fix #1

      (cherry picked from commit f5027a3)
      """
    And success notification is shown 'Cherry-pick successful'
      """
      f5027a3 fix #1
      c123abc fix #2
      """

  Scenario: Cherry-pick 3 commits, where second conflicts with local changes
    Given commit c123abc on branch feature
      """
      fix #2
      M file.txt "feature changes\nmore feature changes"
      """
    Given commit bb6453c on branch feature
      """
      feature content
      M conflict.txt "feature version"
      """
    Given conflict.txt is locally modified:
      """
      master uncommitted content
      """
    When I cherry-pick commits f5027a3, bb6453c and f5027a3
    Then the last commit is
      """
      fix #1

      (cherry picked from commit f5027a3)
      """
    And error notification is shown 'Cherry-pick failed'
      """
      bb6453c feature content
      Your local changes would be overwritten by cherry-pick.
      Commit your changes or stash them to proceed.
      <hr/>
      However cherry-pick succeeded for the following commit:
      f5027a3 fix #1
      """

  Scenario: Cherry-pick 3 commits, where second conflicts with master
    Given commit c123abc on branch feature
      """
      fix #2
      M file.txt "feature changes\nmore feature changes"
      """
    Given commit bb6453c on branch feature
      """
      feature content
      M conflict.txt "feature version"
      """
    Given commit aff6453 on branch master
      """
      master content
      M conflict.txt "master version"
      """
    When I cherry-pick commits f5027a3, bb6453c and c123abc
    Then the last commit is
      """
      fix #1
      
      (cherry picked from commit f5027a3)
      """
    And merge dialog should be shown

  Scenario: Notify if changes have already been applied (IDEA-73548)
    Given commit eef9832 on branch master
      """
      fix #1 manually incorporated
      M file.txt "initial content\nfeature changes"
      """
    When I cherry-pick the commit f5027a3
    Then the last commit is eef9832
    And warning notification is shown 'Nothing to cherry-pick'
      """
      All changes from f5027a3 have already been applied
      """

  Scenario: Cherry-pick 3 commits, second commit have already been applied (IDEA-73548)
    Given commit c123abc on branch feature
      """
      fix #2
      A newfile.txt "initial content"
      """
    Given commit d123abc on branch feature
      """
      fix #3
      M newfile.txt "initial content\nfeature changes"
      """
    Given commit e123abc on branch feature
      """
      fix for f2
      M a.txt "initial content\nfeature changes"
      """
    Given commit e098fed on branch master
      """
      fix for f2 manually incorporated
      M a.txt "initial content\nfeature changes"
      """
    When I cherry-pick commits c123abc, d123abc and e123abc
    Then `git log -2` should return
      """
      fix #3
      (cherry picked from commit d123abc)
      -----
      fix #2
      (cherry picked from commit c123abc)
      """
    And success notification is shown 'Cherry-picked 2 commits from 3'
      """
      c123abc fix #2
      d123abc fix #3
      <hr/>
      e123abc wasn't picked, because all changes from it have already been applied.
      """
