load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "smRunner",
    srcs = ["src"],
    iml_files = ["smRunner.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/testRunner",
        "//tools/idea/.idea/libraries:tcServiceMessages",
    ],
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
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:lang-impl[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/.idea/libraries:tcServiceMessages",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/platform/lang-api[module]",
    ],
)
