load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.projectModel",
    srcs = ["src"],
    iml_files = ["intellij.platform.projectModel.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/core-api:intellij.platform.core",
        "//tools/idea/jps/model-api:intellij.platform.jps.model",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)
