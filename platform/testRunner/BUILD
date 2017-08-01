load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testRunner",
    srcs = ["src"],
    iml_files = ["testRunner.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/xdebugger-api[module]",
    ],
)
