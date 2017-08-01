load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "platform-tests",
    iml_files = ["platform-tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/jps/model-impl:jps-model-impl[module, test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/platform/util:util-tests[module, test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/idea/.idea/libraries:purejavacomm",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
