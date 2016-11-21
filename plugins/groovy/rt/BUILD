load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "groovy_rt",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/plugins/groovy/rt-constants:groovy-rt-constants[module]",
        "//tools/idea/.idea/libraries:griffon-rt",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
)
