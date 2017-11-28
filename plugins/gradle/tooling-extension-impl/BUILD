load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-impl",
    srcs = ["src"],
    iml_files = ["gradle-tooling-extension-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:gradle-tooling-extension-api[module]",
        "//tools/idea/plugins/gradle/lib:guava-jdk5-17.0",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/gradle/lib:gradle-native-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-build-init-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-dependency-management-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-ide-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-java-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-jvm-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-scala-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-platform-base-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-platform-jvm-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-plugins-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-scala-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-testing-base-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-testing-jvm-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-ear-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-reporting-4.0",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-workers-4.0",
    ],
)

iml_module(
    name = "gradle-tooling-extension-tests",
    iml_files = ["gradle-tooling-extension-tests.iml"],
    tags = ["managed"],
    test_resources = ["testData"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle/tooling-extension-impl:gradle-tooling-extension-impl[module]",
        "//tools/idea/plugins/gradle[module]",
        "//tools/idea:jps-builders_and_others[module, test]",
    ],
)
