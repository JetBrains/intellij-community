load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.util",
    srcs = ["src"],
    iml_files = ["intellij.platform.util.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Mac",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:imgscalr",
        "//tools/idea/.idea/libraries:xmlgraphics-commons",
        "//tools/idea/.idea/libraries:batik",
        "//tools/idea/.idea/libraries:lz4-java",
        "//tools/idea/.idea/libraries:xml-apis-ext",
        "//tools/idea/.idea/libraries:commons-compress",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.util.tests",
    iml_files = ["testSrc/intellij.platform.util.tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/platform/util:intellij.platform.util[module, test]",
        "//tools/idea/.idea/libraries:JDOM[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:picocontainer[test]",
        "//tools/idea/.idea/libraries:jna[test]",
        "//tools/idea/platform/core-api:intellij.platform.core[module, test]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module, test]",
        "//tools/idea/.idea/libraries:jetCheck[test]",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
    ],
)
