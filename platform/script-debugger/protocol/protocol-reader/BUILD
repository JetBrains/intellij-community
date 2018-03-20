load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "protocol-reader",
    srcs = ["src"],
    iml_files = ["protocol-reader.iml"],
    package_prefixes = {"src": "org.jetbrains.protocolReader"},
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/script-debugger/protocol/protocol-reader-runtime"],
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
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/script-debugger/protocol/protocol-reader-runtime[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
