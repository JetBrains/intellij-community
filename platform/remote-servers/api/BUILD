load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-api",
    srcs = ["src"],
    iml_files = ["remote-servers-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/remote-servers/agent-rt:remote-servers-agent-rt"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/remote-servers/agent-rt:remote-servers-agent-rt[module]",
    ],
)
