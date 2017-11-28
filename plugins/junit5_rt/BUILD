load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "junit5_rt",
    srcs = ["src"],
    iml_files = ["junit5_rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/junit_rt[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/.idea/libraries:junit5_rt",
    ],
)
