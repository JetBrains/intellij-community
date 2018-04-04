load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.junit.v5.rt",
    srcs = ["src"],
    iml_files = ["intellij.junit.v5.rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/junit_rt:intellij.junit.rt[module]",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
        "//tools/idea/.idea/libraries:junit5_rt",
    ],
)
