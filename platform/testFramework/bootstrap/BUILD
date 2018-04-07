load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.tools.testsBootstrap",
    srcs = ["src"],
    iml_files = ["intellij.tools.testsBootstrap.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/platform/boot:intellij.platform.boot"],
    deps = ["//tools/idea/.idea/libraries:JUnit4"],
)
