load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "util",
    srcs = ["src"],
    iml_files = ["util.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/annotations",
        "//tools/idea/platform/util-rt",
        "//tools/idea/.idea/libraries:Snappy-Java",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Mac",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-resources-en[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util-rt[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/idea/.idea/libraries:OroMatcher",
        "//tools/idea/.idea/libraries:Snappy-Java",
        "//tools/idea/.idea/libraries:imgscalr",
        "//tools/idea/.idea/libraries:xmlgraphics-commons",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:xml-apis-ext",
        "//tools/idea/.idea/libraries:batik",
    ],
)

iml_module(
    name = "util-tests",
    iml_files = ["testSrc/util-tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/platform/util[module, test]",
        "//tools/idea/.idea/libraries:JDOM[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:picocontainer[test]",
        "//tools/idea/.idea/libraries:jna[test]",
        "//tools/idea/platform/core-api[module, test]",
        "//tools/idea/platform/projectModel-impl[module, test]",
    ],
)
