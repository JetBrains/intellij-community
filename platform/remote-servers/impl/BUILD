load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-impl",
    srcs = ["src"],
    iml_files = ["remote-servers-impl.iml"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/xdebugger-api[module]",
    ],
)
