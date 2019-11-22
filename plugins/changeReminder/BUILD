load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.changeReminder",
    srcs = ["src"],
    iml_files = ["intellij.vcs.changeReminder.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
    ],
    visibility = ["//visibility:public"],
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
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/vcs-log/graph:intellij.platform.vcs.log.graph.impl[module]",
        "//tools/idea/platform/vcs-log/impl:intellij.platform.vcs.log.impl[module]",
        "//tools/idea/platform/vcs-log/api:intellij.platform.vcs.log[module]",
        "//tools/idea/plugins/git4idea:intellij.vcs.git[module]",
        "//tools/idea/.idea/libraries:change-reminder-prediction-model",
    ],
)
