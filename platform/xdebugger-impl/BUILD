load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.debugger.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.debugger.impl.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    test_runtime_deps = ["//tools/idea/images:intellij.platform.images"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport:intellij.regexp",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/xdebugger-api:intellij.platform.debugger[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/platform-resources:intellij.platform.resources[module]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module, test]",
        "//tools/idea/xml/impl:intellij.xml.impl[module, test]",
        "//tools/idea/platform/diff-api:intellij.platform.diff[module]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
    ],
)
