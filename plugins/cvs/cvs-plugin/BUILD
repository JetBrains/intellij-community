load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "cvs-plugin",
    srcs = ["src"],
    iml_files = ["cvs-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/cvs/cvs-core[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/plugins/cvs:javacvs-src[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/plugins/cvs:smartcvs-src[module]",
        "//tools/idea/.idea/libraries:microba",
    ],
)
