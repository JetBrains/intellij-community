load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.vcs-log-impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.core-api[module]",
        "//tools/idea/platform/vcs-log/api:idea.vcs-log-api[module]",
        "//tools:idea.vcs-api[module]",
        "//tools/idea/platform/vcs-log/graph:idea.vcs-log-graph[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/vcs-log/graph-api:idea.vcs-log-graph-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/idea/platform/vcs-tests:idea.vcs-tests[module, test]",
    ],
)
