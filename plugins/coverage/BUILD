load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "coverage",
    srcs = ["src"],
    iml_files = ["coverage.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/coverage-common",
        "//tools/idea/java/execution/openapi:execution-openapi",
        "//tools/idea/java/openapi",
        "//tools/idea/plugins/coverage/lib:coverage-report-idea",
        "//tools/idea/plugins/coverage/lib:coverage-report",
        "//tools/idea/plugins/coverage/lib:freemarker",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/coverage-common[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:execution-impl[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/plugins/coverage/lib:coverage-report-idea",
        "//tools/idea/plugins/coverage/lib:coverage-report",
        "//tools/idea/plugins/coverage/lib:freemarker",
        "//tools/idea/plugins/testng_rt[module]",
        "//tools/idea/plugins/junit_rt[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea/.idea/libraries:JaCoCo",
        "//tools/idea/plugins/coverage/coverage_rt[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:compiler-impl_and_others[module, test]",
    ],
)
