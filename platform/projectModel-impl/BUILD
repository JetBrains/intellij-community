load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.projectModel.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.projectModel.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel",
        "//tools/idea/jps/model-serialization:intellij.platform.jps.model.serialization",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/jps/model-impl:intellij.platform.jps.model.impl[module]",
        "//tools/idea/jps/model-serialization:intellij.platform.jps.model.serialization[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
