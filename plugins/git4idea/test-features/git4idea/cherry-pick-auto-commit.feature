Feature: Git Cherry-Pick When Auto-Commit is selected

  Background:
    Given enabled auto-commit in the settings
    Given file file.txt on master:
      """
      first line

      last line (space is to have possibility to avoid conflicts)
      """
    Given branch "feature" with commit "fix_1" by "John Bro" modifying file.txt:
      """
      first line

      last line on branch feature
      """

  Scenario: Simple cherry-pick
    When I cherry-pick the commit "fix_1"
    Then the last commit is
      """
      fix_1
      (cherry picked from commit #hash)
      """
    And notification "Cherry-pick successful" is shown
    And no new changelists are created


