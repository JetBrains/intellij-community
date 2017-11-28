load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "vcs-log-api",
    srcs = ["src"],
    iml_files = ["vcs-log-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util-rt[module]",
        "//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/vcs-api/vcs-api-core[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/vcs-api[module]",
    ],
)
