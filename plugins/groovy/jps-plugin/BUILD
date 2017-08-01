load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "groovy-jps-plugin",
    srcs = ["src"],
    iml_files = ["groovy-jps-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/plugins/groovy/rt-constants:groovy-rt-constants[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/.idea/libraries:Groovy-Eclipse-Batch",
        "//tools/idea/java/compiler/instrumentation-util[module]",
    ],
)
