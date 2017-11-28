load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "copyright",
    srcs = ["src"],
    iml_files = ["copyright.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/.idea/libraries:Velocity",
        "//tools/idea:xml[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea:testExtensions[module, test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:configuration-store-impl[module, test]",
    ],
)
