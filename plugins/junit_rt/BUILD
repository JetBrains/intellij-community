load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.junit.rt",
    srcs = ["src"],
    iml_files = ["intellij.junit.rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
    ],
)
