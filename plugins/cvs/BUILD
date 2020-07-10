load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.cvs.javacvs",
    srcs = ["javacvs-src"],
    iml_files = ["javacvs-src/intellij.vcs.cvs.javacvs.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:Trove4j",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.cvs.smartcvs",
    srcs = ["smartcvs-src"],
    iml_files = ["smartcvs-src/intellij.vcs.cvs.smartcvs.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/cvs:intellij.vcs.cvs.javacvs[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
    ],
)
