load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "coverage_rt",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/testng_rt[module]",
        "//tools/idea/plugins/junit_rt[module]",
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/.idea/libraries:Coverage",
    ],
)
