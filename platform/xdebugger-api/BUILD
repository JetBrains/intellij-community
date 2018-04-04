load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.debugger",
    srcs = ["src"],
    iml_files = ["intellij.platform.debugger.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/lang-api:intellij.platform.lang"],
    deps = ["//tools/idea/platform/lang-api:intellij.platform.lang[module]"],
)
