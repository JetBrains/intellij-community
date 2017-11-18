load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "eclipse",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["eclipse.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/bootstrap[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/plugins/eclipse/jps-plugin:eclipse-jps-plugin[module]",
        "//tools/idea/plugins/eclipse/common-eclipse-util[module]",
        "//tools/idea:testExtensions[module, test]",
    ],
)
