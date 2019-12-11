load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.testExtensions",
    srcs = ["src"],
    iml_files = ["intellij.platform.testExtensions.iml"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/xml/dom-openapi:intellij.xml.dom"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/idea/.idea/libraries:assertJ"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:assertJ",
        "//tools/idea/.idea/libraries:memoryfilesystem",
        "//tools/idea/.idea/libraries:snakeyaml",
    ],
)
