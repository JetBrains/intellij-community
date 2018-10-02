load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.java",
    srcs = ["src"],
    iml_files = ["intellij.gradle.java.iml"],
    package_prefixes = {"src": "org.jetbrains.plugins.gradle"},
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/gradle:intellij.gradle.common"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle:intellij.gradle[module]",
        "//tools/idea/plugins/gradle:intellij.gradle.common[module]",
        "//tools/idea/plugins/coverage:intellij.java.coverage[module]",
        "//tools/idea/plugins/gradle/jps-plugin:intellij.gradle.jps[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/plugins/groovy:intellij.groovy[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/plugins/maven:intellij.maven[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/platform/smRunner:intellij.platform.smRunner[module]",
        "//tools/idea/.idea/libraries:Kryo",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
        "//tools/idea/.idea/libraries:XStream",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.java.tests",
    iml_files = ["intellij.gradle.java.tests.iml"],
    package_prefixes = {"testSources": "org.jetbrains.plugins.gradle"},
    test_resources = ["testData"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/gradle:intellij.gradle.tests"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/plugins/gradle/java:intellij.gradle.java[module, test]",
        "//tools/idea/plugins/gradle:intellij.gradle.tests[module, test]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.tests[module, test]",
        "//tools/idea/plugins/maven/maven-server-api:intellij.maven.server[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
