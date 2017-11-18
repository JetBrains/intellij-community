load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "junit",
    srcs = ["src"],
    iml_files = ["junit.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/smRunner"],
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
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:execution-impl[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/.idea/libraries:JUnit3",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/plugins/junit_rt[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/java-indexing-api[module]",
        "//tools/idea/platform/smRunner[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:opentest4j",
    ],
)
