load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "coverage-common",
    srcs = ["src"],
    iml_files = ["coverage-common.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:Coverage",
        "//tools/idea/.idea/libraries:Trove4j",
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
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:Coverage",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/platform/lvcs-api[module]",
        "//tools/idea/platform/vcs-api[module]",
    ],
)
