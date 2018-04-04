load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.coverage.rt",
    srcs = ["src"],
    iml_files = ["intellij.java.coverage.rt.iml"],
    package_prefixes = {"src": "com.intellij.coverage"},
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/testng_rt:intellij.testng.rt[module]",
        "//tools/idea/plugins/junit_rt:intellij.junit.rt[module]",
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/.idea/libraries:Coverage",
    ],
)
