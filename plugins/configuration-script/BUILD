load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.configurationScript",
    # do not sort: must match IML order
    srcs = [
        "src",
        "snakeyaml-engine",
    ],
    iml_files = ["intellij.configurationScript.iml"],
    package_prefixes = {
        "test": "com.intellij.configurationScript",
        "src": "com.intellij.configurationScript",
        "snakeyaml-engine": "org.snakeyaml.engine.v1",
    },
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kotlin-reflect",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/json:intellij.json[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea/plugins/yaml:intellij.yaml[module, test]",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl[module]",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer[module]",
        "//tools/idea/.idea/libraries:jackson[test]",
        "//tools/idea/platform/service-container:intellij.platform.serviceContainer[module]",
    ],
)
