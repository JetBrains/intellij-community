load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-impl",
    srcs = ["src"],
    iml_files = ["gradle-tooling-extension-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
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
        "//tools/idea/plugins/gradle/lib:gradle-native-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-build-init-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-dependency-management-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-ide-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-java-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-jvm-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-language-scala-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-platform-base-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-platform-jvm-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-plugins-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-scala-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-testing-base-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-api/lib:gradle-testing-jvm-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-ear-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-reporting-3.5",
        "//tools/idea/plugins/gradle/tooling-extension-impl/lib:gradle-workers-3.5",
    ],
)

iml_module(
    name = "gradle-tooling-extension-tests",
    iml_files = ["gradle-tooling-extension-tests.iml"],
    tags = ["managed"],
    test_resources = ["testData"],
    test_runtime_deps = ["//tools/idea:testExtensions"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle/tooling-extension-impl:gradle-tooling-extension-impl[module]",
        "//tools/idea/plugins/gradle[module]",
        "//tools/idea:compiler-impl_and_others[module, test]",
    ],
)
