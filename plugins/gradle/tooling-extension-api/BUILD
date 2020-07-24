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
        "//prebuilts/tools/common/m2/repository/org/codehaus/groovy/groovy/2.4.17:jar",
        "//prebuilts/tools/common/m2/repository/org/codehaus/groovy/groovy-ant/2.4.17:jar",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:ion",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//prebuilts/tools/common/m2/repository/org/codehaus/groovy/groovy/2.4.17:jar",
        "//prebuilts/tools/common/m2/repository/org/codehaus/groovy/groovy-ant/2.4.17:jar",
    ],
)
