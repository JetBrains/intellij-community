load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.configurationScript",
    srcs = ["src"],
    iml_files = ["intellij.configurationScript.iml"],
    package_prefixes = {
        "test": "com.intellij.configurationScript",
        "src": "com.intellij.configurationScript",
    },
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/json:intellij.json[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/.idea/libraries:snakeyaml",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/.idea/libraries:gson[test]",
        "//tools/idea/plugins/yaml:intellij.yaml[module, test]",
    ],
)
