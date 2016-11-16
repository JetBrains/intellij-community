load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.git4idea",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
    ],
    tags = ["managed"],
    test_srcs = [
        "tests",
        "test-features",
        "test-stepdefs",
    ],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/dvcs-impl:idea.dvcs-impl",
        "//tools/idea/platform/dvcs-api:idea.dvcs-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.vcs-api[module]",
        "//tools/idea/.idea/libraries:trilead-ssh2",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:idea.lang-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools:idea.lvcs-api[module]",
        "//tools/idea/.idea/libraries:TestNG[test]",
        "//tools:idea.platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/plugins/git4idea/rt:idea.git4idea-rt[module]",
        "//tools/idea/.idea/libraries:ini4j-0.5.2-patched",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/platform/dvcs-impl:idea.dvcs-impl[module]",
        "//tools/idea/.idea/libraries:cucumber-core[test]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/build/cucumber-test-runner:idea.cucumber-test-runner[module, test]",
        "//tools/idea/platform/vcs-log/api:idea.vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:idea.vcs-log-impl[module]",
        "//tools/idea/platform/vcs-log/graph:idea.vcs-log-graph[module]",
        "//tools/idea/platform/dvcs-api:idea.dvcs-api[module]",
        "//tools:idea.built-in-server-api[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:cucumber-java[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/idea/platform/vcs-tests:idea.vcs-tests[module, test]",
    ],
)
