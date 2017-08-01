load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-tests",
    srcs = ["src"],
    iml_files = ["vcs-tests.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/platform/vcs-api/vcs-api-core[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/platform-api[module]",
    ],
)
