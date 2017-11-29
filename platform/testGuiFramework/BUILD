load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testGuiFramework",
    srcs = ["src"],
    iml_files = ["testGuiFramework.iml"],
    resources = ["res"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea:platform-impl",
        "//tools/idea/.idea/libraries:fest",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl[module]",
        "//tools/idea/.idea/libraries:fest",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/bootstrap[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea:xdebugger-impl[module]",
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea:external-system-impl[module]",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea:testFramework[module]",
        "//tools/idea:diff-impl[module, test]",
        "//tools/idea/.idea/libraries:jediterm-pty",
    ],
)
