load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "editorconfig",
    srcs = ["src"],
    iml_files = ["editorconfig.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/editorconfig/lib:editorconfig-core-java",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/lang-api[module]",
    ],
)
