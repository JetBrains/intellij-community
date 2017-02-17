load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-api",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/plugins/gradle/lib:gradle-tooling-api-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-messaging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-wrapper-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-logging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-native-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-process-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-dependency-management-3.2",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/plugins/gradle/lib:gradle-tooling-api-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-messaging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-wrapper-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-logging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-native-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-process-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-dependency-management-3.2",
        "//tools/idea/plugins/gradle/lib:guava-jdk5-17.0",
        "//tools/idea/.idea/libraries:Groovy",
    ],
)
