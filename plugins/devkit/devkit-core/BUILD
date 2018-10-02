load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.devkit",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.devkit.iml"],
    package_prefixes = {"src": "org.jetbrains.idea.devkit"},
    resources = ["resources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/plugins/devkit/jps-plugin:intellij.devkit.jps",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/plugins/properties:intellij.properties[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/plugins/devkit/devkit-core:lib/dtdparser113",
        "//tools/idea/spellchecker:intellij.spellchecker[module]",
        "//tools/idea/plugins/properties/properties-psi-api:intellij.properties.psi[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module]",
        "//tools/idea/images:intellij.platform.images[module]",
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
        "//tools/idea/plugins/gradle/java:intellij.gradle.java[module]",
        "//tools/idea/jvm/jvm-analysis-api:intellij.jvm.analysis[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/json:intellij.json[module]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "lib/dtdparser113",
    jars = ["lib/dtdparser113.jar"],
    visibility = ["//visibility:public"],
)
