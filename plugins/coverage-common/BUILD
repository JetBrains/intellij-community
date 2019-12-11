load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.coverage",
    srcs = ["src"],
    iml_files = ["intellij.platform.coverage.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:intellij-coverage",
        "//tools/idea/.idea/libraries:intellij-test-discovery",
        "//tools/idea/.idea/libraries:Trove4j",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:intellij-coverage",
        "//tools/idea/.idea/libraries:intellij-test-discovery",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/lvcs-api:intellij.platform.lvcs[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
    ],
)
