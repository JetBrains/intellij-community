load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-jps-plugin",
    srcs = ["src"],
    iml_files = ["gradle-jps-plugin.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Ant"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/java-runtime",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
    ],
)
