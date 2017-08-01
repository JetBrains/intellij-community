load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ant",
    srcs = ["src"],
    iml_files = ["ant.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["tests/src"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea:platform-impl_and_others",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/plugins/properties[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/plugins/ant/jps-plugin:ant-jps-plugin[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
    ],
)
