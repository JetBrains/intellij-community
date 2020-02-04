load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.remoteServers",
    srcs = ["src"],
    iml_files = ["intellij.platform.remoteServers.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/remote-servers/agent-rt:intellij.platform.remoteServers.agent.rt"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/remote-servers/agent-rt:intellij.platform.remoteServers.agent.rt[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
