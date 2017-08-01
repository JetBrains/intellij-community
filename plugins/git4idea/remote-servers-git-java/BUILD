load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-git-java",
    srcs = ["src"],
    iml_files = ["remote-servers-git-java.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/git4idea/remote-servers-git[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/openapi[module]",
    ],
)
