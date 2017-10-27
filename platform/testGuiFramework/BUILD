load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testGuiFramework",
    srcs = ["src"],
    iml_files = ["testGuiFramework.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:fest"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:fest",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/bootstrap[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea/platform/platform-main[module]",
        "//tools/idea/.idea/libraries:jsr305",
    ],
)
