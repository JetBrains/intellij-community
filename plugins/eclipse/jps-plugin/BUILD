load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "eclipse-jps-plugin",
    srcs = ["src"],
    iml_files = ["eclipse-jps-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/plugins/eclipse/common-eclipse-util[module]",
    ],
)
