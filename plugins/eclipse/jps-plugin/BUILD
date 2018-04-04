load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.eclipse.jps",
    srcs = ["src"],
    iml_files = ["intellij.eclipse.jps.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/jps/model-serialization:intellij.platform.jps.model.serialization[module]",
        "//tools/idea/plugins/eclipse/common-eclipse-util:intellij.eclipse.common[module]",
    ],
)
