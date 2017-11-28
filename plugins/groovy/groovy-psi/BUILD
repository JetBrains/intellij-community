load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "groovy-psi",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    bundle_data = glob([
        "resources/standardDsls/**",
        "resources/conf/**",
    ]),
    iml_files = ["groovy-psi.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea/plugins/junit[module, test]",
        "//tools/idea/java/java-indexing-api[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:xml-psi-impl[module]",
        "//tools/idea/platform/editor-ui-ex[module]",
        "//tools/idea:java-analysis-impl[module]",
        "//tools/idea/java/java-structure-view[module]",
        "//tools/idea/plugins/properties/properties-psi-impl[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
    ],
)
