load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.langInjection.tests",
    iml_files = ["intellij.platform.langInjection.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/ant/jps-plugin:intellij.ant.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/ByteCodeViewer:intellij.java.byteCodeViewer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/java/structuralsearch-java:intellij.java.structuralSearch",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/plugins/IntelliLang:intellij.platform.langInjection[module]",
        "//tools/idea/plugins/IntelliLang:intellij.xml.langInjection[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/plugins/yaml:intellij.yaml[module]",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection[module]",
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/java/compiler/instrumentation-util:intellij.java.compiler.instrumentationUtil[module]",
        "//tools/idea/.idea/libraries:assertJ",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps[module]",
    ],
)
