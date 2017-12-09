load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "copyright",
    srcs = ["src"],
    iml_files = ["copyright.iml"],
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
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/.idea/libraries:Velocity",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/testFramework/extensions:testExtensions[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/platform/configuration-store-impl[module, test]",
    ],
)
