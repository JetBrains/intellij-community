load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.native.tooling",
    srcs = ["src"],
    iml_files = ["intellij.gradle.native.tooling.iml"],
    package_prefixes = {"src": "org.jetbrains.plugins.gradle.nativeplatform.tooling"},
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension[module]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
        "//tools/idea/.idea/libraries:Gradle",
    ],
)
