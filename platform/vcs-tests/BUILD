load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-tests",
    srcs = ["src"],
    iml_files = ["vcs-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
        "//tools/idea:images",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea:testFramework[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea:vcs-impl[module]",
        "//tools/idea/platform/vcs-api/vcs-api-core[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:diff-tests[module, test]",
    ],
)
