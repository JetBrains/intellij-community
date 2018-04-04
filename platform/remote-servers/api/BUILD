load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.remoteServers",
    srcs = ["src"],
    iml_files = ["intellij.platform.remoteServers.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/remote-servers/agent-rt:intellij.platform.remoteServers.agent.rt"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/remote-servers/agent-rt:intellij.platform.remoteServers.agent.rt[module]",
    ],
)
