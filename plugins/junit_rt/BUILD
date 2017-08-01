load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "junit_rt",
    srcs = ["src"],
    iml_files = ["junit_rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/java/java-runtime[module]",
    ],
)
