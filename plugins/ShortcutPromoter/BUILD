load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ShortcutPromoter",
    srcs = ["src"],
    iml_files = ["ShortcutPromoter.iml"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/platform-api[module]",
    ],
)
