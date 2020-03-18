load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.configurationScript",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.configurationScript.iml"],
    package_prefixes = {
        "test": "com.intellij.configurationScript",
        "src": "com.intellij.configurationScript",
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
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/workspaceModel-ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/.idea/libraries:javassist",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:javax.annotation-api",
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
        "//tools/idea/.idea/libraries:snakeyaml-engine",
    ],
)
