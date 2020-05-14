load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.util",
    srcs = ["src"],
    bundle_data = ["//tools/idea/bin"],  # Used by NativeLibraryLoader et al.
    iml_files = ["intellij.platform.util.iml"],
    javacopts = [
        "-Xep:ComplexBooleanConstant:WARN",
        "-Xep:IdentityBinaryExpression:WARN",
    ],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt",
        "//tools/idea/platform/util-class-loader:intellij.platform.util.classLoader",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/platform/util-class-loader:intellij.platform.util.classLoader[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:lz4-java",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:HdrHistogram",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.util.tests",
    iml_files = ["testSrc/intellij.platform.util.tests.iml"],
    javacopts = ["-Xep:ModifyingCollectionWithItself:WARN"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/.idea/libraries:javassist",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:javax.annotation-api",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/platform/util:intellij.platform.util[module, test]",
        "//tools/idea/.idea/libraries:JDOM[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:jna[test]",
        "//tools/idea/platform/core-api:intellij.platform.core[module, test]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module, test]",
        "//tools/idea/.idea/libraries:jetCheck[test]",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea/.idea/libraries:bouncy-castle-provider[test]",
        "//tools/idea/.idea/libraries:memoryfilesystem[test]",
        "//tools/idea/.idea/libraries:jimfs[test]",
        "//tools/idea/.idea/libraries:HdrHistogram[test]",
        "//tools/idea/.idea/libraries:protobuf[test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.concurrency",
    srcs = ["concurrency"],
    iml_files = ["intellij.platform.concurrency.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kotlinx-coroutines-jdk8",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.util.ui",
    srcs = ["ui/src"],
    iml_files = ["intellij.platform.util.ui.iml"],
    resources = ["ui/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Mac",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:batik-transcoder",
        "//tools/idea/.idea/libraries:imgscalr",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:xml-apis-ext",
        "//tools/idea/.idea/libraries:Java_Compatibility",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:batik-codec",
    ],
)
