load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testGuiFramework",
    srcs = ["src"],
    iml_files = ["testGuiFramework.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:fest"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl[module]",
        "//tools/idea/.idea/libraries:fest",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/bootstrap[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea:xdebugger-impl[module]",
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea:external-system-impl[module]",
        "//tools/idea/platform/platform-main[module]",
        "//tools/idea/.idea/libraries:jsr305",
    ],
)
