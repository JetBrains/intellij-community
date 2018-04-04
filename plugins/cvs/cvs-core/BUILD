load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.vcs.cvs.core",
    srcs = ["src"],
    iml_files = ["intellij.vcs.cvs.core.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/plugins/cvs:intellij.vcs.cvs.javacvs[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/plugins/cvs/lib:trilead-ssh2-build213",
    ],
)
