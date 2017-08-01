load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "cvs-core",
    srcs = ["src"],
    iml_files = ["cvs-core.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/plugins/cvs:javacvs-src[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/plugins/cvs/lib:trilead-ssh2-build213",
    ],
)
