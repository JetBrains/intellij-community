load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-jps-plugin",
    srcs = ["src"],
    iml_files = ["gradle-jps-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Ant"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
    ],
)
