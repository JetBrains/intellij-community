load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "google-app-engine-jps-plugin",
    srcs = ["src"],
    iml_files = ["google-app-engine-jps-plugin.iml"],
    tags = ["managed"],
    test_srcs = ["testSource"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module, test]",
        "//tools/idea/plugins/google-app-engine/runtime:appEngine-runtime[module]",
        "//tools/idea/jps/model-serialization:jps-serialization-tests[module, test]",
    ],
)
