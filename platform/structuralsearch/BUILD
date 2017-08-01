load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch",
    srcs = ["source"],
    iml_files = ["structuralsearch.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/duplicates-analysis[module]",
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
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/java/structuralsearch-java[module]",
        "//tools/idea/plugins/java-i18n[module]",
    ],
)
