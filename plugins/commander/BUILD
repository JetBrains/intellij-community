load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "commander",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
