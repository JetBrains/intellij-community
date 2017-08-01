load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "projectModel-api",
    srcs = ["src"],
    iml_files = ["projectModel-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/core-api",
        "//tools/idea/jps/model-api:jps-model-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:Xerces",
    ],
)
