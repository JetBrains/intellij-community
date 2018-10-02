load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.devkit.tests",
    srcs = ["src"],
    iml_files = ["intellij.devkit.tests.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/devkit/devkit-core:intellij.devkit",
        "//tools/idea:intellij.platform.jps.build_and_others",
        "//tools/idea/.idea/libraries:assertJ",
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework",
        "//tools/idea/xml/dom-tests:intellij.xml.dom.tests",
        "//tools/idea:intellij.java.compiler.tests",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler",
        "//tools/idea/jvm/jvm-analysis-api:intellij.jvm.analysis",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/devkit/jps-plugin:intellij.devkit.jps",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/devkit/devkit-core:intellij.devkit[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:TestNG[test]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module]",
        "//tools/idea/xml/dom-tests:intellij.xml.dom.tests[module]",
        "//tools/idea:intellij.java.compiler.tests[module]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/jvm/jvm-analysis-api:intellij.jvm.analysis[module]",
    ],
)
