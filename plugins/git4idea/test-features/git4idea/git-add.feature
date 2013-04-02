Feature: Git Add
  As a happy Git integration user,
  I want to be able to add files to the VCS.

  Scenario: Add one simple file
    Given unversioned file unv.txt
    When I add unv.txt to VCS
    Then unv.txt should become ADDED

  Scenario: Add a directory
    Given unversioned file dir/unv.txt
    When I add dir to VCS
    Then dir/unv.txt should become ADDED

  @nestedroot
  Scenario: Add file from different roots
    Given unversioned file unv.txt
    And unversioned file community/com.txt
    When I add unv.txt, community/com.txt to VCS
    Then unv.txt should become ADDED
    And community/com.txt should become ADDED

  @nestedroot
  Scenario: Add a file from subroot by calling "add" on the upper root
    Given unversioned file community/com.txt
    When I add the project dir to VCS
    Then community/com.txt should become ADDED

  @nestedroot
  Scenario: Add a file from nested root and the directory above the nested root
    Given unversioned file community/com.txt
    And unversioned file community/unv.txt
    When I add the project dir, community/com.txt to VCS
    Then community/com.txt should become ADDED
    And community/unv.txt should become ADDED