load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.gradle.toolingExtension",
    srcs = ["src"],
    iml_files = ["intellij.gradle.toolingExtension.iml"],
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
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt[module]",
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Groovy",
    ],
)
