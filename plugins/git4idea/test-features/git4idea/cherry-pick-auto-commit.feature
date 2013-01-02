Feature: Git Cherry-Pick When Auto-Commit is selected

Background:
  Given enabled auto-commit in the settings
  Given new committed file file.txt 'initial'
  Given new committed file conflict.txt 'initial content for conflict'
  Given branch feature
  And commit f5027a3 on branch feature
    """
    fix #1
    -----
    Author: John Bro
    Changes:
    M file.txt "feature changes"
    """

  # conflicting commits
  Given commit aff6453 on branch master
    """
    master content
    -----
    Changes:
    M conflict.txt "master version"
    """
  Given commit bb6453c on branch feature
    """
    feature content
    -----
    Changes:
    M conflict.txt "feature version"
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
      master content"""
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
      -----
      Changes:
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
    When I cherry-pick the commit bb6453c
    Then merge dialog should be shown

  Scenario: Unresolved conflict with cherry-picked commit should produce a changelist
    When I cherry-pick the commit bb6453c and don't resolve conflicts
    Then active changelist is 'feature content (cherry picked from commit bb6453c)'
    And warning notification is shown 'Cherry-picked with conflicts'
      """
      bb6453c feature content
      Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>
      """

  Scenario: Resolved conflict should show commit dialog
    When I cherry-pick the commit bb6453c and resolve conflicts
    Then commit dialog should be shown

  Scenario: Resolve conflict and agree to commit
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
    When I cherry-pick the commit bb6453c, resolve conflicts and don't commit
    Then active changelist is 'feature content (cherry picked from commit bb6453c)'
    And no notification is shown

  Scenario: Cherry-pick 2 commits
    Given commit c123abc on branch feature
      """
      fix #2
      -----
      Author: John Bro
      Changes:
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
      -----
      Author: John Bro
      Changes:
      M file.txt "feature changes\nmore feature changes"
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
      -----
      Author: John Bro
      Changes:
      M file.txt "feature changes\nmore feature changes"
      """
    When I cherry-pick commits f5027a3, bb6453c and f5027a3
    Then the last commit is
      """
      fix #1
      (cherry picked from commit f5027a3)
      """
    And merge dialog should be shown