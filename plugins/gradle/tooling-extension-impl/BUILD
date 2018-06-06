load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.toolingExtension.impl",
    srcs = ["src"],
    iml_files = ["intellij.gradle.toolingExtension.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:gson",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.toolingExtension.tests",
    iml_files = ["intellij.gradle.toolingExtension.tests.iml"],
    test_resources = ["testData"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
        "//tools/idea/plugins/gradle:intellij.gradle.common[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
    ],
)
