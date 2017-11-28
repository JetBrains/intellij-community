load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "script-debugger-backend",
    srcs = ["src"],
    iml_files = ["script-debugger-backend.iml"],
    package_prefixes = {"src": "org.jetbrains"},
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/script-debugger/protocol/protocol-reader-runtime[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/.idea/libraries:Netty",
    ],
)
