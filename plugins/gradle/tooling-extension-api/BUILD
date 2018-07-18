load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.toolingExtension",
    srcs = ["src"],
    iml_files = ["intellij.gradle.toolingExtension.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Groovy",
    ],
)
