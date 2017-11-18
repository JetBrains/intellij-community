load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellilang-jps-plugin",
    srcs = ["src"],
    iml_files = ["intellilang-jps-plugin.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:xml",
        "//tools/idea:spellchecker",
        "//tools/idea:relaxng",
        "//tools/idea:json",
        "//tools/idea:images",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/java/java-runtime"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/java/compiler/instrumentation-util[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
    ],
)
