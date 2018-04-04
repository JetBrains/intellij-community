load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.cucumber.jvmFormatter",
    srcs = ["src"],
    iml_files = ["intellij.cucumber.jvmFormatter.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:cucumber-core",
        "//tools/idea/.idea/libraries:cucumber-core_2.0.1",
    ],
)
