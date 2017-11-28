load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "script-debugger-ui",
    srcs = ["src"],
    iml_files = ["script-debugger-ui.iml"],
    package_prefixes = {"testSrc": "org.jetbrains.debugger"},
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
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
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/platform/script-debugger/backend:script-debugger-backend[module]",
        "//tools/idea:xdebugger-impl[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:lang-impl[module]",
    ],
)
