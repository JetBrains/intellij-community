load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.projectModel",
    srcs = ["src"],
    iml_files = ["intellij.platform.projectModel.iml"],
    resources = ["resources"],
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
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/util:intellij.platform.concurrency[module]",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
