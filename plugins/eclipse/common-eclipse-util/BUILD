load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.eclipse.common",
    srcs = ["src"],
    iml_files = ["intellij.eclipse.common.iml"],
    package_prefixes = {"src": "org.jetbrains.idea.eclipse"},
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/jps/model-serialization:intellij.platform.jps.model.serialization[module]",
    ],
)
