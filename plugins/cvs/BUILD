load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "javacvs-src",
    srcs = ["javacvs-src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/platform/util[module]"],
)

iml_module(
    name = "smartcvs-src",
    srcs = ["smartcvs-src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/cvs:javacvs-src[module]",
        "//tools/idea/platform/util[module]",
    ],
)
