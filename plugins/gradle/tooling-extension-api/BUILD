load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-api",
    srcs = ["src"],
    iml_files = ["gradle-tooling-extension-api.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
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
    exports = [
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:compiler-impl_and_others[module, test]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/plugins/gradle/lib:guava-jdk5-17.0",
        "//tools/idea/.idea/libraries:Groovy",
    ],
)
