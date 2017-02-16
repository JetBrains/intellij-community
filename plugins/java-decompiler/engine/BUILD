load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-decompiler-engine",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
