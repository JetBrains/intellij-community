load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch-groovy",
    srcs = ["src"],
    iml_files = ["structuralsearch-groovy.iml"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea:testExtensions"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/plugins/groovy:jetgroovy[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/platform/structuralsearch:structuralsearch-tests[module, test]",
        "//tools/idea:compiler-impl_and_others[module, test]",
        "//tools/idea/platform/duplicates-analysis[module]",
    ],
)
