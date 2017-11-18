load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "commander",
    srcs = ["src"],
    iml_files = ["commander.iml"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea:testExtensions"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:diff-impl[module]",
    ],
)
