load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "hg4idea",
    srcs = ["src"],
    iml_files = ["hg4idea.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:xml",
        "//tools/idea:spellchecker",
        "//tools/idea:relaxng",
        "//tools/idea:json",
        "//tools/idea:xdebugger-impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:TestNG[test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:vcs-impl[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/dvcs-impl[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:vcs-log-impl[module]",
        "//tools/idea/platform/dvcs-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/platform/vcs-tests[module, test]",
    ],
)
