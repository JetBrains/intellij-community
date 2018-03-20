load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testFramework.core",
    srcs = ["src"],
    iml_files = ["testFramework.core.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)
