load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.cvs.core",
    srcs = ["src"],
    iml_files = ["intellij.vcs.cvs.core.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/plugins/cvs:intellij.vcs.cvs.javacvs[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/plugins/cvs/lib:trilead-ssh2-build213",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)
