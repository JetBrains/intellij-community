load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle",
    srcs = ["src"],
    iml_files = ["gradle.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/external-system-api",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/plugins/gradle/tooling-extension-api:gradle-tooling-extension-api",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/plugins/gradle/lib:commons-io-2.2",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/external-system-api[module]",
        "//tools/idea/platform/external-system-impl[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:gradle-tooling-extension-api[module]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:gradle-tooling-extension-impl[module]",
        "//tools/idea/plugins/gradle/jps-plugin:gradle-jps-plugin[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea/plugins/groovy:jetgroovy[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/java/execution/impl:execution-impl[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/plugins/maven[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/plugins/gradle/lib:commons-io-2.2",
        "//tools/idea/plugins/gradle/lib:commons-lang-2.6",
        "//tools/idea/platform/smRunner[module]",
        "//tools/idea/.idea/libraries:Kryo",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/coverage[module]",
    ],
)

iml_module(
    name = "gradle-tests",
    iml_files = ["gradle-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
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
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea/plugins/gradle[module, test]",
        "//tools/idea/plugins/gradle/tooling-extension-impl:gradle-tooling-extension-tests[module, test]",
        "//tools/idea/plugins/maven/maven-server-api[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
