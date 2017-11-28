load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-api",
    srcs = ["src"],
    iml_files = ["vcs-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/platform-api",
        "//tools/idea/platform/vcs-api/vcs-api-core",
        "//tools/idea/platform/diff-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/.idea/libraries:microba",
        "//tools/idea/platform/vcs-api/vcs-api-core[module]",
        "//tools/idea/platform/diff-api[module]",
        "//tools/idea/.idea/libraries:StreamEx",
    ],
)
