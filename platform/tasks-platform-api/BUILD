load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-platform-api",
    srcs = ["src"],
    iml_files = ["tasks-platform-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/vcs-api[module]",
    ],
)
