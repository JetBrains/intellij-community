load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellilang-jps-plugin",
    srcs = ["src"],
    iml_files = ["intellilang-jps-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/java/compiler/instrumentation-util[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
    ],
)
