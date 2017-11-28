load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch",
    srcs = ["source"],
    iml_files = ["structuralsearch.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
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
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea:xml[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/duplicates-analysis[module]",
        "//tools/idea:lang-impl[module]",
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
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/java/structuralsearch-java[module]",
        "//tools/idea/plugins/java-i18n[module]",
    ],
)
