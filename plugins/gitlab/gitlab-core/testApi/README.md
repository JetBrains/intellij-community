# API Tests

### What are these tests?

These tests check our assumptions of the GitLab API.
Each test does only a few simple things:

1. Check that the test should be run for the version of GitLab tested against.
2. Acquire an (un)authenticated API client
3. Call any number of API interactions
4. Verify that results from the API are as expected (error or not)

For spinning up a GitLab server and parsing environment variables for inputs, these tests rely
on `org.jetbrains.plugins.gitlab.apitests.GitLabApiTestCase`.

### Why are these tests?

In August 2023, we encountered issues with the launch of the GitLab plugin as it turned out API interactions
were only (manually) tested for gitlab.com, which ran the latest version of GitLab (16.3 at the time).
Since then, we introduced annotations to help document what functions are dependent on what versions of GitLab
so that as much functionality can be available for older versions of GitLab (`org.jetbrains.plugins.gitlab.api.SinceGitLab`).
These tests check that our assumptions of the GitLab API are valid or whether our lower bounds for when a feature was
introduced should be adjusted, for instance.
These are not meant as full integration tests, but only to test our assumptions about the API.
Full integration tests are hard to setup and might not even be necessary.
Instead, we will produce tests that test internal interactions on top of this set of API tests.

### How do I add a test?

If a relevant test class does not exist already, create a class in `org.jetbrains.plugins.gitlab.apitests` that extends `GitLabApiTestCase`.
To add a test, add a method with the following structure:

```kotlin
fun test() = runTest {
  checkVersion(after(v(14, 0)))

  requiresAuthentication { api ->
    // Do things with API and perform assertions
  }
}
```

`checkVersion` will check the given predicate. If the predicate fails,
so the current GitLab server version tested against is before 14.0, the test is skipped.
`requiresAuthentication` makes sure to run the test only with an authenticated API.

### How do I run these tests?

*To run these tests, you need external data*. For the time being, ask Chris Lemaire for access if you need to run the tests locally.
Data will be provided as a tarball containing GitLab server configurations and data, everything needed to run a reproducible environment of
GitLab.
Say we get `ce-14.0.0.tar.gz` and want to run the tests for GitLab CE version 14.1 (data packages can be used for multiple versions).
Then we perform the following steps to run the tests locally:

1. Untar the tarball as follows: `sudo tar -xzvf /path/to/ce-14.0.0.tar.gz -C /target/location`, where `/target/location` could be anywhere.
   `/tmp` might be a good target. The tar will be unpacked as a directory called `current` and should ideally be renamed to something
   like `gitlab-test`.
   Note: it's important to do this as superuser, because ownership of files matters for the GitLab docker image to start properly.
2. Select the 'GitLab API Tests' run configuration in IntelliJ and adjust the following environment variables:
  * `idea_test_gitlab_api_data_path` to the resulting directory from step 1, for instance `/tmp/gitlab-test`.
    `/tmp/gitlab-test` should contain the directories `config` and `data` in this case.
  * `idea_test_gitlab_api_version` to the GitLab server version to test for, including patch version, `14.1.0` in our case.
  * `idea_test_gitlab_api_edition` to the GitLab server edition to test for, `ce` in our case. Only `ce` and `ee` are allowed as values.
3. Run the configuration. For reproducibility, it's best to perform all steps over again if re-running, even though tests will usually pass
   just fine without re-seeding the data.
