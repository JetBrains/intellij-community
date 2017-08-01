load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "script-debugger-backend",
    srcs = ["src"],
    iml_files = ["script-debugger-backend.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/script-debugger/protocol/protocol-reader-runtime[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Netty",
    ],
)
