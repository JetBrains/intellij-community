load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-api-core",
    srcs = ["src"],
    iml_files = ["vcs-api-core.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/diff-api[module]",
    ],
)
