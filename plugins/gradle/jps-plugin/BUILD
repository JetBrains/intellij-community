load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-jps-plugin",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Ant"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/plugins/gradle/lib:gradle-tooling-api-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-messaging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-wrapper-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-native-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-resources-3.2",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
    ],
)
