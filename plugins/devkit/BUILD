load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "devkit",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["devkit.iml"],
    package_prefixes = {"src": "org.jetbrains.idea.devkit"},
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/plugins/devkit/jps-plugin:devkit-jps-plugin",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea:dom-openapi[module]",
        "//tools/idea:execution-impl[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/plugins/properties[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/plugins/devkit/lib:dtdparser113",
        "//tools/idea:spellchecker[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:testFramework[module]",
        "//tools/idea:dom-impl[module]",
        "//tools/idea:images[module]",
    ],
)

iml_module(
    name = "devkit-tests",
    iml_files = ["devkit-tests.iml"],
    package_prefixes = {"testSources": "org.jetbrains.idea.devkit"},
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:external-system-impl",
        "//tools/idea:testExtensions",
    ],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea/plugins/devkit/jps-plugin:devkit-jps-plugin",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:TestNG[test]",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/xml/dom-tests[module, test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/plugins/devkit[module]",
        "//tools/idea:dom-impl[module, test]",
    ],
)
