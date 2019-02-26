load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.eclipse",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.eclipse.iml"],
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/plugins/eclipse/jps-plugin:intellij.eclipse.jps[module]",
        "//tools/idea/plugins/eclipse/common-eclipse-util:intellij.eclipse.common[module]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
    ],
)
