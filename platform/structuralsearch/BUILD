load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.structuralSearch",
    srcs = ["source"],
    iml_files = ["intellij.platform.structuralSearch.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/platform/duplicates-analysis:intellij.platform.duplicates.analysis[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.structuralSearch.tests",
    iml_files = ["intellij.platform.structuralSearch.tests.iml"],
    test_srcs = ["testSource"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/structuralsearch:intellij.platform.structuralSearch[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/java/structuralsearch-java:intellij.java.structuralSearch[module]",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n[module]",
    ],
)
