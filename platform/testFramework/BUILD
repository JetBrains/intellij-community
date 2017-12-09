load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testFramework",
    srcs = ["src"],
    iml_files = ["testFramework.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/lang-api",
        "//tools/idea/platform/lang-impl",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/platform-impl",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Mocks",
        "//tools/idea/java/java-runtime",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/jetCheck",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/resources-en",
        "//tools/idea/platform/platform-resources",
        "//tools/idea/xml/dom-impl",
        "//tools/idea/xml/relaxng",
        "//tools/idea/images",
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/platform/testFramework/bootstrap:tests_bootstrap[module]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/boot[module]",
        "//tools/idea/jetCheck[module, test]",
    ],
)
