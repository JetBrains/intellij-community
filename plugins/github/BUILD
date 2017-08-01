load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "github",
    srcs = ["src"],
    iml_files = ["github.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/tasks/tasks-core",
        "//tools/idea/plugins/tasks/tasks-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/plugins/git4idea[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/plugins/tasks/tasks-core[module]",
        "//tools/idea/plugins/tasks/tasks-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/dvcs-impl[module]",
        "//tools/idea/platform/dvcs-api[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:vcs-log-impl[module]",
        "//tools/idea/plugins/tasks/tasks-core/jira[module, test]",
        "//tools/idea/platform/vcs-tests[module, test]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
