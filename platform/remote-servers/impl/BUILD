load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-impl",
    srcs = ["src"],
    iml_files = ["remote-servers-impl.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/xdebugger-api[module]",
    ],
)
