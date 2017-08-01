load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-log-graph",
    srcs = ["src"],
    iml_files = ["vcs-log-graph.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
    ],
)
