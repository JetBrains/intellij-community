load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xdebugger-impl",
    srcs = ["src"],
    iml_files = ["xdebugger-impl.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/platform/platform-resources[module]",
        "//tools/idea/xml/dom-impl[module, test]",
        "//tools/idea/xml/impl:xml[module, test]",
        "//tools/idea/platform/diff-api[module]",
        "//tools/idea/platform/testFramework/extensions:testExtensions[module, test]",
    ],
)
