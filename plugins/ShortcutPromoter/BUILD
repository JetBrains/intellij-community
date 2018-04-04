load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.shortcutPromoter",
    srcs = ["src"],
    iml_files = ["intellij.shortcutPromoter.iml"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
    ],
)
