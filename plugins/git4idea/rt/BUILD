load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.git.rt",
    srcs = ["src"],
    iml_files = ["intellij.vcs.git.rt.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/.idea/libraries:trilead-ssh2",
        "//tools/idea/.idea/libraries:XmlRPC",
    ],
)
