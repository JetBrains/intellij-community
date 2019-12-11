load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.testFramework",
    srcs = ["src"],
    iml_files = ["intellij.platform.testFramework.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/lang-api:intellij.platform.lang",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/java/java-runtime:intellij.java.rt",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:jetCheck",
        "//tools/idea/platform/testFramework/core:intellij.platform.testFramework.core",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/resources-en:intellij.java.resources.en",
        "//tools/idea/platform/platform-resources:intellij.platform.resources",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/RegExpSupport:intellij.regexp",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
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
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/platform/testFramework/bootstrap:intellij.tools.testsBootstrap[module]",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/boot:intellij.platform.boot[module]",
        "//tools/idea/.idea/libraries:jetCheck",
        "//tools/idea/platform/testFramework/core:intellij.platform.testFramework.core[module]",
        "//tools/idea/.idea/libraries:kotlin-test",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/platform/service-container:intellij.platform.serviceContainer[module]",
    ],
)
