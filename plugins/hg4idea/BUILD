load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "hg4idea",
    srcs = ["src"],
    iml_files = ["hg4idea.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:TestNG[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/vcs-impl[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/dvcs-impl[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:vcs-log-impl[module]",
        "//tools/idea/platform/dvcs-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/vcs-tests[module, test]",
    ],
)
