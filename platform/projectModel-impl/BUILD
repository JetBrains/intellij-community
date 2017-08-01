load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "projectModel-impl",
    srcs = ["src"],
    iml_files = ["projectModel-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/projectModel-api",
        "//tools/idea/jps/model-serialization:jps-model-serialization",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Snappy-Java",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
