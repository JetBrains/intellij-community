load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "smRunner",
    srcs = ["src"],
    iml_files = ["smRunner.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/testRunner",
        "//tools/idea/.idea/libraries:tcServiceMessages",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/.idea/libraries:tcServiceMessages",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/platform/lang-api[module]",
    ],
)
