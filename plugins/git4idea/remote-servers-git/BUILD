load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.remoteServers.git",
    srcs = ["src"],
    iml_files = ["intellij.platform.remoteServers.git.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/remote-servers/api:intellij.platform.remoteServers",
        "//tools/idea/plugins/git4idea:intellij.vcs.git",
        "//tools/idea/platform/remote-servers/impl:intellij.platform.remoteServers.impl",
        "//tools/idea/platform/lang-api:intellij.platform.lang",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/remote-servers/agent-rt:intellij.platform.remoteServers.agent.rt[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/platform/remote-servers/api:intellij.platform.remoteServers[module]",
        "//tools/idea/plugins/git4idea:intellij.vcs.git[module]",
        "//tools/idea/platform/remote-servers/impl:intellij.platform.remoteServers.impl[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/platform/dvcs-api:intellij.platform.vcs.dvcs[module]",
    ],
)
