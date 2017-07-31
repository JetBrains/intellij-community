load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "script-debugger-ui",
    srcs = ["src"],
    package_prefixes = {"testSrc": "org.jetbrains.debugger"},
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/script-debugger/backend:script-debugger-backend[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
