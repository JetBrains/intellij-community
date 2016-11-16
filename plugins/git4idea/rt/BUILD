load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.git4idea-rt",
    srcs = ["src"],
    javacopts = ["-extra_checks:off"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
        "//tools/idea/.idea/libraries:trilead-ssh2",
        "//tools/idea/.idea/libraries:XmlRPC",
    ],
)
