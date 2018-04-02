load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "protocol-model-generator",
    # do not sort: must match IML order
    srcs = [
        "src",
        "generated",
    ],
    iml_files = ["protocol-model-generator.iml"],
    package_prefixes = {"src": "org.jetbrains.protocolModelGenerator"},
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
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/script-debugger/protocol/schema-reader-generator[module]",
        "//tools/idea/platform/script-debugger/protocol/protocol-reader[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/util-rt[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
