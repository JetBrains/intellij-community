load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-log-graph",
    srcs = ["src"],
    iml_files = ["vcs-log-graph.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:lvcs-impl",
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:relaxng",
        "//tools/idea:json",
        "//tools/idea:images",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
    ],
)
