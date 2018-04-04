load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.tasks",
    srcs = ["src"],
    iml_files = ["intellij.platform.tasks.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
    ],
)
