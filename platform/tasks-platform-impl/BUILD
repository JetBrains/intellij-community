load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-platform-impl",
    srcs = ["src"],
    iml_files = ["tasks-platform-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/tasks-platform-api[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/xdebugger-impl[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/.idea/libraries:http-client-3.1",
        "//tools/idea/platform/vcs-impl[module]",
    ],
)
