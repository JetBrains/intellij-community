load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "platform-main",
    srcs = ["src"],
    iml_files = ["platform-main.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/bootstrap[module]",
    ],
)
