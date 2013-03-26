@remote
Feature: Support remote operation over HTTP.
  As a happy Git integration user
  I want to be able to work with Git remotes via HTTP protocol.

  Scenario: Clone from HTTP url containing username
    When I clone http://gituser@deb6-vm7-git/projectA.git
    Then I should be asked for the password
    When I provide password 'gitpassword'
    Then repository should be cloned to projectA

  Scenario: Clone from HTTP url without username
    When I clone http://deb6-vm7-git/projectA.git
    Then I should be asked for the username
    When I provide username 'gituser'
    Then I should be asked for the password
    When I provide password 'gitpassword'
    Then repository should be cloned to projectA

  Scenario: Clone from HTTP url containing username, provide incorrect password
    When I clone http://gituser@deb6-vm7-git/projectA.git
    Then I should be asked for the password
    When I provide password 'incorrect'
    Then repository should not be cloned to projectA
    And error notification is shown 'Clone failed'
       """
       fatal: Authentication failed
       """
