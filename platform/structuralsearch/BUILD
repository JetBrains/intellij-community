load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch",
    srcs = ["source"],
    iml_files = ["structuralsearch.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/duplicates-analysis[module]",
        "//tools/idea/platform/lang-impl[module]",
    ],
)

iml_module(
    name = "structuralsearch-tests",
    iml_files = ["structuralsearch-tests.iml"],
    tags = ["managed"],
    test_srcs = ["testSource"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/java/structuralsearch-java[module]",
        "//tools/idea/plugins/java-i18n[module]",
    ],
)
