load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "cucumber-jvm-formatter",
    srcs = ["src"],
    iml_files = ["cucumber-jvm-formatter.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:cucumber-core",
        "//tools/idea/.idea/libraries:io.cucumber_cucumber-core_2.0.1",
    ],
)
