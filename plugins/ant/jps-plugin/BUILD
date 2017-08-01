load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ant-jps-plugin",
    srcs = ["src"],
    iml_files = ["ant-jps-plugin.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/jps/model-serialization:jps-serialization-tests[module, test]",
    ],
)
