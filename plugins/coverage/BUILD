load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.coverage",
    srcs = ["src"],
    iml_files = ["intellij.java.coverage.iml"],
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/coverage-common:intellij.platform.coverage",
        "//tools/idea/java/execution/openapi:intellij.java.execution",
        "//tools/idea/java/openapi:intellij.java",
        "//tools/idea/plugins/coverage/lib:coverage-report-idea",
        "//tools/idea/plugins/coverage/lib:coverage-report",
        "//tools/idea/plugins/coverage/lib:freemarker",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/coverage-common:intellij.platform.coverage[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner[module]",
        "//tools/idea/plugins/coverage/lib:coverage-report-idea",
        "//tools/idea/plugins/coverage/lib:coverage-report",
        "//tools/idea/plugins/coverage/lib:freemarker",
        "//tools/idea/plugins/testng_rt:intellij.testng.rt[module]",
        "//tools/idea/plugins/junit_rt:intellij.junit.rt[module]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/.idea/libraries:JaCoCo",
        "//tools/idea/plugins/coverage/coverage_rt:intellij.java.coverage.rt[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
    ],
)
