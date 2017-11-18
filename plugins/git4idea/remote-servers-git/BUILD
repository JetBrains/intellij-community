load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-git",
    srcs = ["src"],
    iml_files = ["remote-servers-git.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:execution-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/remote-servers/api:remote-servers-api",
        "//tools/idea/plugins/git4idea",
        "//tools/idea/platform/remote-servers/impl:remote-servers-impl",
        "//tools/idea/platform/lang-api",
        "//tools/idea:lang-impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/remote-servers/agent-rt:remote-servers-agent-rt[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea/plugins/git4idea[module]",
        "//tools/idea/platform/remote-servers/impl:remote-servers-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:vcs-impl[module]",
        "//tools/idea/platform/dvcs-api[module]",
    ],
)
