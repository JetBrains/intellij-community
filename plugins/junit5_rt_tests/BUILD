load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.junit.v5.rt.tests",
    iml_files = ["intellij.junit.v5.rt.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/junit5_rt:intellij.junit.v5.rt[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module, test]",
        "//tools/idea/plugins/junit:intellij.junit[module, test]",
        "//tools/idea:intellij.java.tests[module, test]",
        "//tools/idea/aether-dependency-resolver:intellij.java.aetherDependencyResolver[module, test]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module, test]",
        "//tools/idea/.idea/libraries:JUnit5[test]",
        "//tools/idea/.idea/libraries:junit5_rt[test]",
        "//prebuilts/tools/common/m2/repository/org/apiguardian/apiguardian-api/1.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/jupiter/junit-jupiter-api/5.1.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/jupiter/junit-jupiter-engine/5.1.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/platform/junit-platform-commons/1.1.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/platform/junit-platform-engine/1.1.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/opentest4j/opentest4j/1.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/vintage/junit-vintage-engine/5.1.0:jar[test]",
    ],
)
