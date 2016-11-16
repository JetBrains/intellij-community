load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.vcs-log-api",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:idea.vcs-log-graph-api"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.util-rt[module]",
        "//tools/idea/platform/vcs-log/graph-api:idea.vcs-log-graph-api[module]",
        "//tools:idea.core-api[module]",
        "//tools:idea.vcs-api-core[module]",
        "//tools:idea.editor-ui-api[module]",
    ],
)
