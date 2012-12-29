Feature: Git Cherry-Pick When Auto-Commit is selected

Background:
  Given enabled auto-commit in the settings
  Given file file.txt "initial" on master
  And commit f5027a3 on branch feature
    """
    fix #1
    -----
    Author: John Bro
    Changes:
    M file.txt "feature changes"
    """

  Scenario: Simple cherry-pick
    When I cherry-pick the commit f5027a3
    Then the last commit is
      """
      fix #1
      (cherry picked from commit f5027a3)
      """
    And there is notification 'Cherry-pick successful'
    And no new changelists are created

  Scenario: Dirty tree, conflicting with the commit
    Given file.txt is locally modified:
      """
      master
      """
    When I cherry-pick the commit f5027a3
    Then nothing is committed
    And error notification 'Cherry-pick failed' is shown:
      """
      f5027a3 fix #1
      error: Your local changes to the following files would be overwritten by merge:
      """

