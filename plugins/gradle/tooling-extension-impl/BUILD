load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.toolingExtension.impl",
    srcs = ["src"],
    iml_files = ["intellij.gradle.toolingExtension.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/external-system-rt:intellij.platform.externalSystem.rt[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/object-serializer:intellij.platform.objectSerializer.annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:ion",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.gradle.toolingExtension.tests",
    iml_files = ["intellij.gradle.toolingExtension.tests.iml"],
    test_resources = ["testData"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/ant/jps-plugin:intellij.ant.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/ByteCodeViewer:intellij.java.byteCodeViewer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
    ],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle/tooling-extension-impl:intellij.gradle.toolingExtension.impl[module]",
        "//tools/idea/plugins/gradle:intellij.gradle.common[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//prebuilts/tools/common/m2/repository/io/github/classgraph/classgraph/4.8.29:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/jeasy/easy-random-core/4.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/objenesis/objenesis/3.0.1:jar[test]",
        "//tools/idea/.idea/libraries:ion[test]",
    ],
)
