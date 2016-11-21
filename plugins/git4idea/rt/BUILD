load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "git4idea-rt",
    srcs = ["src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util-rt[module]",
        "//tools/idea/.idea/libraries:trilead-ssh2",
        "//tools/idea/.idea/libraries:XmlRPC",
    ],
)
