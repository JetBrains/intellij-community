load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch",
    srcs = ["source"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/duplicates-analysis[module]",
    ],
)
