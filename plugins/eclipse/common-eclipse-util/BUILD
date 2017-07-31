load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "common-eclipse-util",
    srcs = ["src"],
    package_prefixes = {"src": "org.jetbrains.idea.eclipse"},
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
    ],
)
