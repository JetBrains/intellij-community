load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-impl",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/gradle/lib:gradle-tooling-api-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-messaging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-wrapper-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-native-3.2",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:gradle-tooling-extension-api[module]",
        "//tools/idea/plugins/gradle/lib:gradle-tooling-api-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-messaging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-wrapper-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-native-3.2",
        "//tools/idea/plugins/gradle/lib:guava-jdk5-17.0",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-build-init-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-ide-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-java-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-jvm-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-platform-base-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-platform-jvm-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-plugins-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-testing-base-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-testing-jvm-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-scala-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-scala-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-reporting-3.2",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-ear-3.2",
    ],
)

iml_module(
    name = "gradle-tooling-extension-tests",
    tags = ["managed"],
    test_resources = ["testData"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle/tooling-extension-impl:gradle-tooling-extension-impl[module]",
        "//tools/idea/plugins/gradle[module]",
        "//tools/idea:compiler-impl_and_others[module]",
    ],
)
