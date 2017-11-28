load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "commander",
    srcs = ["src"],
    iml_files = ["commander.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:diff-impl[module]",
    ],
)
