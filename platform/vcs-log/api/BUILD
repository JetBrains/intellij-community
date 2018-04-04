load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.vcs.log",
    srcs = ["src"],
    iml_files = ["intellij.platform.vcs.log.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:intellij.platform.vcs.log.graph"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/platform/vcs-log/graph-api:intellij.platform.vcs.log.graph[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/vcs-api/vcs-api-core:intellij.platform.vcs.core[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
    ],
)
