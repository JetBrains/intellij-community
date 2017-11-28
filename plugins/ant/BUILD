load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ant",
    srcs = ["src"],
    iml_files = ["ant.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea:external-system-impl"],
    test_srcs = ["tests/src"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea:dom-openapi",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea:execution-impl[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/plugins/properties[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea:dom-openapi[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/plugins/ant/jps-plugin:ant-jps-plugin[module]",
        "//tools/idea:dom-impl[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
    ],
)
