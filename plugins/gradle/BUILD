load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle",
    srcs = ["src"],
    iml_files = ["intellij.gradle.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension[module]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
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
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/platform/smRunner:intellij.platform.smRunner[module]",
        "//tools/idea/.idea/libraries:Kryo",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/coverage:intellij.java.coverage[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.tests",
    iml_files = ["intellij.gradle.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_srcs = [
        "testData",
        "testSources",
    ],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/plugins/gradle:intellij.gradle[module, test]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.tests[module, test]",
        "//tools/idea/plugins/maven/maven-server-api:intellij.maven.server[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module, test]",
    ],
)
