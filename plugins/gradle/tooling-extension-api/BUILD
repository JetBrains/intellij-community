load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-api",
    srcs = ["src"],
    iml_files = ["gradle-tooling-extension-api.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
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
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/plugins/gradle/lib:guava-jdk5-17.0",
        "//tools/idea/.idea/libraries:Groovy",
    ],
)
